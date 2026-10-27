package com.paymatch.matching;

import com.paymatch.common.TextNormalizer;

import java.util.List;

public record CounterpartySnapshot(Long id, String name, String nip, String iban) {

    // legal forms are left out when comparing names: banks write them in many different ways
    private static final List<String> LEGAL_FORMS = List.of(
            "SPOLKA Z OGRANICZONA ODPOWIEDZIALNOSCIA", "SPOLKA AKCYJNA", "SPOLKA JAWNA", "SPOLKA KOMANDYTOWA",
            "SP Z O O", "SP Z OO", "SP ZOO", "S A", "SA", "SP J", "SPJ", "SP K", "SPK");

    /** "Hurtownia Gęś i Wspólnicy Sp.j." -> "HURTOWNIA GES I WSPOLNICY" */
    public static String normalizeName(String name) {
        String normalized = " " + TextNormalizer.upperAscii(name).replaceAll("[^A-Z0-9]+", " ").trim() + " ";
        for (String form : LEGAL_FORMS) {
            normalized = normalized.replace(" " + form + " ", " ");
        }
        return normalized.trim().replaceAll("\\s+", " ");
    }

    String normalizedName() {
        return normalizeName(name);
    }
}
