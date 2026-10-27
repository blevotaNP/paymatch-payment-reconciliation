package com.paymatch.transaction;

import com.paymatch.common.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final BankTransactionRepository transactionRepository;

    public TransactionService(BankTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(TransactionFilter filter, Pageable pageable) {
        return transactionRepository.findAll(TransactionSpecifications.matching(filter), pageable)
                .map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionDetailsResponse get(Long id) {
        BankTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Transaction", id));
        return new TransactionDetailsResponse(TransactionResponse.from(transaction),
                transaction.getStatementImport().getFileName(), transaction.getRawData());
    }
}
