package com.paymatch.matching;

import com.paymatch.common.validation.Nip;
import com.paymatch.transaction.BankTransaction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything the rules look at besides the transaction itself, loaded once per matching run. */
public class MatchingContext {

    private static final Pattern NIP_IN_TEXT = Pattern.compile("(?<!\\d)(\\d{3}-?\\d{3}-?\\d{2}-?\\d{2}|\\d{10})(?!\\d)");
    // short names like "ACME" are fine: a name match only counts together with a matching amount
    private static final int MIN_NAME_LENGTH = 4;

    private final List<InvoiceSnapshot> invoices;
    private final List<CounterpartySnapshot> counterparties;
    private final Map<String, CounterpartySnapshot> byAccountDigits = new HashMap<>();
    private final Map<String, CounterpartySnapshot> byNip = new HashMap<>();
    private final Set<String> invoicePrefixes = new HashSet<>();

    public MatchingContext(List<InvoiceSnapshot> invoices, List<CounterpartySnapshot> counterparties) {
        this.invoices = invoices;
        this.counterparties = counterparties;
        for (CounterpartySnapshot counterparty : counterparties) {
            if (counterparty.iban() != null) {
                byAccountDigits.put(accountDigits(counterparty.iban()), counterparty);
            }
            if (counterparty.nip() != null) {
                byNip.put(counterparty.nip(), counterparty);
            }
        }
        for (InvoiceSnapshot invoice : invoices) {
            if (!invoice.prefix().isEmpty()) {
                invoicePrefixes.add(invoice.prefix());
            }
        }
    }

    public List<InvoiceSnapshot> invoices(String currency) {
        return invoices.stream().filter(invoice -> invoice.getCurrency().equals(currency)).toList();
    }

    /** Open invoices of one counterparty, oldest due date first. */
    public List<InvoiceSnapshot> openInvoicesOf(Long counterpartyId, String currency) {
        return invoices.stream()
                .filter(invoice -> invoice.getCounterpartyId().equals(counterpartyId))
                .filter(invoice -> invoice.getCurrency().equals(currency))
                .filter(InvoiceSnapshot::isOpen)
                .sorted(Comparator.comparing(InvoiceSnapshot::getDueDate).thenComparing(InvoiceSnapshot::getId))
                .toList();
    }

    public Set<String> invoicePrefixes() {
        return invoicePrefixes;
    }

    /** Tries the sender's account first, then a NIP written in the title, then the sender's name. */
    public Optional<Identification> identify(BankTransaction tx) {
        if (tx.getCounterpartyAccount() != null) {
            CounterpartySnapshot byAccount = byAccountDigits.get(accountDigits(tx.getCounterpartyAccount()));
            if (byAccount != null) {
                return Optional.of(new Identification(byAccount, Identification.Method.ACCOUNT, tx.getCounterpartyAccount()));
            }
        }
        if (tx.getTitle() != null) {
            Matcher m = NIP_IN_TEXT.matcher(tx.getTitle());
            while (m.find()) {
                String nip = Nip.normalize(m.group(1));
                if (Nip.isValid(nip) && byNip.containsKey(nip)) {
                    return Optional.of(new Identification(byNip.get(nip), Identification.Method.NIP, nip));
                }
            }
        }
        if (tx.getCounterpartyName() != null) {
            String senderName = " " + CounterpartySnapshot.normalizeName(tx.getCounterpartyName()) + " ";
            return counterparties.stream()
                    .filter(c -> c.normalizedName().length() >= MIN_NAME_LENGTH)
                    .filter(c -> senderName.contains(" " + c.normalizedName() + " "))
                    // "ACME" and "ACME POLSKA" both match "ACME POLSKA SP Z O O": take the more specific one
                    .max(Comparator.comparingInt(c -> c.normalizedName().length()))
                    .map(c -> new Identification(c, Identification.Method.NAME, tx.getCounterpartyName()));
        }
        return Optional.empty();
    }

    /** Last 26 digits: the Polish account number without the country code. */
    private static String accountDigits(String account) {
        String digits = account.replaceAll("[^0-9]", "");
        return digits.length() > 26 ? digits.substring(digits.length() - 26) : digits;
    }
}
