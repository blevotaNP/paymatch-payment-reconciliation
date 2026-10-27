package com.paymatch.counterparty;

import com.paymatch.common.error.ConflictException;
import com.paymatch.common.error.NotFoundException;
import com.paymatch.common.validation.Iban;
import com.paymatch.common.validation.Nip;
import com.paymatch.invoice.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CounterpartyService {

    private final CounterpartyRepository counterpartyRepository;
    private final InvoiceRepository invoiceRepository;

    public CounterpartyService(CounterpartyRepository counterpartyRepository, InvoiceRepository invoiceRepository) {
        this.counterpartyRepository = counterpartyRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public Page<Counterparty> search(String search, Pageable pageable) {
        if (!StringUtils.hasText(search)) {
            return counterpartyRepository.findAll(pageable);
        }
        String term = search.trim();
        return counterpartyRepository.findByNameContainingIgnoreCaseOrNipContaining(term, term, pageable);
    }

    @Transactional(readOnly = true)
    public Counterparty get(Long id) {
        return counterpartyRepository.findById(id).orElseThrow(() -> NotFoundException.of("Counterparty", id));
    }

    @Transactional
    public Counterparty create(CounterpartyRequest request) {
        String nip = normalizeNip(request.nip());
        if (nip != null && counterpartyRepository.existsByNip(nip)) {
            throw new ConflictException("Counterparty with NIP " + nip + " already exists");
        }
        return counterpartyRepository.save(new Counterparty(request.name().trim(), nip, normalizeIban(request.iban())));
    }

    @Transactional
    public Counterparty update(Long id, CounterpartyRequest request) {
        Counterparty counterparty = get(id);
        String nip = normalizeNip(request.nip());
        if (nip != null && counterpartyRepository.existsByNipAndIdNot(nip, id)) {
            throw new ConflictException("Counterparty with NIP " + nip + " already exists");
        }
        counterparty.setName(request.name().trim());
        counterparty.setNip(nip);
        counterparty.setIban(normalizeIban(request.iban()));
        return counterparty;
    }

    @Transactional
    public void delete(Long id) {
        Counterparty counterparty = get(id);
        if (invoiceRepository.existsByCounterpartyId(id)) {
            throw new ConflictException("Counterparty has invoices and cannot be deleted");
        }
        counterpartyRepository.delete(counterparty);
    }

    /**
     * Used by the invoice CSV import: matches by NIP first, then by exact name for clients without NIP.
     * Fills in a missing IBAN on an existing counterparty, but never overwrites data that is already there.
     */
    @Transactional
    public Counterparty findOrCreate(String name, String rawNip, String rawIban) {
        String nip = normalizeNip(rawNip);
        String iban = normalizeIban(rawIban);
        Counterparty counterparty = (nip != null
                ? counterpartyRepository.findByNip(nip)
                : counterpartyRepository.findFirstByNipIsNullAndNameIgnoreCase(name.trim()))
                .orElseGet(() -> counterpartyRepository.save(new Counterparty(name.trim(), nip, iban)));
        if (counterparty.getIban() == null && iban != null) {
            counterparty.setIban(iban);
        }
        return counterparty;
    }

    private static String normalizeNip(String nip) {
        return StringUtils.hasText(nip) ? Nip.normalize(nip) : null;
    }

    private static String normalizeIban(String iban) {
        return StringUtils.hasText(iban) ? Iban.normalize(iban) : null;
    }
}
