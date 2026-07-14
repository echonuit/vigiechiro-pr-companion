package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import java.util.ArrayList;
import java.util.List;

/// Accumulateur d'une réactivation en cours (#1302) : ce qui est revenu, ce qui a été **refusé** et
/// pourquoi, ce qui manque encore. Mutable et sans état caché - c'est un carnet de comptes, pas un
/// service.
///
/// Il vit à part parce que **deux voies** l'alimentent (les séquences retrouvées telles quelles, les
/// séquences régénérées depuis les bruts) et qu'elles doivent compter de la même façon.
final class BilanReactivation {

    int reactivees;
    int manquantes;
    int dejaPresentes;
    NiveauConfiance confianceMinimale;
    final List<EcartReactivation> ecarts = new ArrayList<>();

    /// Une séquence (ou un brut) rebranché, sur telle preuve.
    void accepter(NiveauConfiance niveau) {
        reactivees++;
        retenirConfiance(niveau);
    }

    /// Un fichier **refusé**, avec son motif - en disant **combien** de fichiers portaient ce nom :
    /// « aucun des 2 » se comprend, là où un motif au singulier laisserait croire qu'un seul fichier a
    /// été regardé.
    void refuser(String nomFichier, String motif, int nombreHomonymes) {
        String complet = nombreHomonymes <= 1
                ? motif
                : motif + " (aucun des " + nombreHomonymes + " fichiers de ce nom" + " n'a été reconnu)";
        ecarts.add(new EcartReactivation(nomFichier, complet));
    }

    /// Ajoute le bilan d'**un** brut régénéré au bilan de la nuit.
    void absorber(BilanReactivation partiel) {
        reactivees += partiel.reactivees;
        manquantes += partiel.manquantes;
        dejaPresentes += partiel.dejaPresentes;
        ecarts.addAll(partiel.ecarts);
        if (partiel.confianceMinimale != null) {
            retenirConfiance(partiel.confianceMinimale);
        }
    }

    /// Retient le niveau **le plus faible** : c'est celui qui qualifie honnêtement la réactivation
    /// entière (une seule séquence vérifiée à la structurelle seule suffit à ramener l'ensemble à
    /// `FORTE`).
    private void retenirConfiance(NiveauConfiance niveau) {
        if (confianceMinimale == null || niveau.ordinal() > confianceMinimale.ordinal()) {
            confianceMinimale = niveau;
        }
    }
}
