package com.securebank.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.securebank.dto.TransactionDtos.Direction;
import com.securebank.dto.TransactionDtos.TransactionResponse;
import com.securebank.entity.Account;
import com.securebank.entity.BankTransaction;
import com.securebank.exception.BadRequestException;
import com.securebank.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builds a monthly account statement as a PDF: opening balance, every transaction, closing balance. */
@Service
public class StatementService {

    private static final DateTimeFormatter ROW_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final Color BRAND = new Color(15, 76, 129);
    private static final Color LIGHT = new Color(240, 244, 248);

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    public StatementService(TransactionRepository transactionRepository, AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public byte[] monthlyStatement(Long accountId, YearMonth month, String email) {
        if (month.isAfter(YearMonth.now())) {
            throw new BadRequestException("Statements are only available for past and current months");
        }
        Account account = accountService.findOwnedAccount(accountId, email);
        if (account.getAccountNumber() == null) {
            throw new BadRequestException("This account has not been approved yet");
        }

        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal opening = transactionRepository.findLatestBefore(accountId, start, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(t -> balanceAfter(t, accountId))
                .orElse(BigDecimal.ZERO.setScale(2));
        List<TransactionResponse> rows = transactionRepository.findForStatement(accountId, start, end).stream()
                .map(t -> TransactionResponse.forAccount(t, accountId))
                .toList();
        BigDecimal closing = rows.isEmpty() ? opening : rows.get(rows.size() - 1).balanceAfter();

        return render(account, month, opening, closing, rows);
    }

    private byte[] render(Account account, YearMonth month, BigDecimal opening, BigDecimal closing,
                          List<TransactionResponse> rows) {
        NumberFormat inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        inr.setMinimumFractionDigits(2);
        inr.setMaximumFractionDigits(2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9);

        doc.add(new Paragraph("SecureBank", title));
        doc.add(new Paragraph("Account statement for " + month.format(MONTH), bold));
        doc.add(Chunk.NEWLINE);

        PdfPTable info = new PdfPTable(new float[]{1, 2});
        info.setWidthPercentage(60);
        info.setHorizontalAlignment(Element.ALIGN_LEFT);
        addInfo(info, "Account holder", account.getOwner().getName(), bold, normal);
        addInfo(info, "Account number", account.getAccountNumber(), bold, normal);
        addInfo(info, "Account type", account.getType().name(), bold, normal);
        addInfo(info, "Opening balance", "Rs. " + inr.format(opening), bold, normal);
        addInfo(info, "Closing balance", "Rs. " + inr.format(closing), bold, normal);
        doc.add(info);
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{2.2f, 3.6f, 1.6f, 1.6f, 1.8f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : List.of("Date", "Details", "Debit (Rs.)", "Credit (Rs.)", "Balance (Rs.)")) {
            PdfPCell c = new PdfPCell(new Phrase(h, header));
            c.setBackgroundColor(BRAND);
            c.setPadding(5);
            table.addCell(c);
        }
        if (rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No transactions in this month", cell));
            empty.setColspan(5);
            empty.setPadding(8);
            table.addCell(empty);
        }
        boolean shade = false;
        for (TransactionResponse r : rows) {
            String details = r.counterparty() + (r.description() != null ? " - " + r.description() : "")
                    + "\nRef " + r.reference().substring(0, 8).toUpperCase();
            boolean debit = r.direction() == Direction.DEBIT;
            addRow(table, cell, shade, r.createdAt().format(ROW_TIME), details,
                    debit ? inr.format(r.amount()) : "", debit ? "" : inr.format(r.amount()), inr.format(r.balanceAfter()));
            shade = !shade;
        }
        doc.add(table);
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("This is a computer-generated statement from a demo project and needs no signature.", small));
        doc.close();
        return out.toByteArray();
    }

    private void addInfo(PdfPTable t, String label, String value, Font bold, Font normal) {
        PdfPCell l = new PdfPCell(new Phrase(label, bold));
        PdfPCell v = new PdfPCell(new Phrase(value, normal));
        l.setBorder(Rectangle.NO_BORDER);
        v.setBorder(Rectangle.NO_BORDER);
        l.setPaddingBottom(4);
        v.setPaddingBottom(4);
        t.addCell(l);
        t.addCell(v);
    }

    private void addRow(PdfPTable t, Font font, boolean shade, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i], font));
            c.setPadding(5);
            if (i >= 2) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            if (shade) {
                c.setBackgroundColor(LIGHT);
            }
            t.addCell(c);
        }
    }

    private BigDecimal balanceAfter(BankTransaction t, Long accountId) {
        boolean debit = t.getFromAccount() != null && Objects.equals(t.getFromAccount().getId(), accountId);
        return debit ? t.getFromBalanceAfter() : t.getToBalanceAfter();
    }
}
