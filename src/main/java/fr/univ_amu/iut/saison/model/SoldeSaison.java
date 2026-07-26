package fr.univ_amu.iut.saison.model;

import fr.univ_amu.iut.passage.model.FenetreSaisonniere;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/// Solde de la saison d'un observateur pour une année : une **ligne par point suivi**, plus les
/// décomptes d'en-tête. Ceux-ci sont **dérivés des mêmes lignes** : l'en-tête et le tableau ne
/// peuvent donc pas diverger (critère d'acceptation de #2356).
///
/// @param annee année de la saison
/// @param aujourdhui date courante (injectée), pour les échéances de fenêtre
/// @param lignes une ligne par point suivi (peut être vide : aucun point déclaré)
public record SoldeSaison(int annee, LocalDate aujourdhui, List<LigneSaison> lignes) {

    /// Copie défensive de la liste (immutabilité du record).
    public SoldeSaison {
        lignes = List.copyOf(lignes);
    }

    /// Nombre de points suivis (une ligne par point).
    public int pointsSuivis() {
        return lignes.size();
    }

    /// Nombre de passages **réalisés** (présents et exploitables) sur l'ensemble des points. Une nuit
    /// inexploitable n'est **pas** comptée : elle reste à refaire.
    public long passagesFaits() {
        return cases().filter(CasePassage::faite).count();
    }

    /// Nombre de passages **attendus** : deux par point suivi (protocole PointFixeStandard).
    public int passagesAttendus() {
        return pointsSuivis() * 2;
    }

    /// Nombre de points **à jour** (aucune action restante).
    public long pointsAJour() {
        return lignes.stream().filter(LigneSaison::aJour).count();
    }

    /// Premier jour où le **premier** passage devient attendu (début de la fenêtre R3 du passage 1).
    /// Guide un observateur qui n'a encore aucune nuit.
    public LocalDate premierPassageAttenduDes() {
        return FenetreSaisonniere.pour(1, annee).orElseThrow().debut();
    }

    /// Dernier jour de la fenêtre du **second** passage (échéance de fin de saison).
    public LocalDate echeanceSecondPassage() {
        return FenetreSaisonniere.pour(2, annee).orElseThrow().fin();
    }

    /// Nombre de jours restants avant la fermeture de la fenêtre du second passage (négatif si la
    /// fenêtre est déjà passée).
    public long joursAvantEcheanceSecondPassage() {
        return ChronoUnit.DAYS.between(aujourdhui, echeanceSecondPassage());
    }

    /// Nombre de points dont le **second passage n'est pas valablement fait** (absent ou
    /// inexploitable) alors que sa fenêtre **n'est pas close** : ce sont eux que le signalement de
    /// fin de saison concerne. L'application **signale**, elle n'alerte pas.
    public long pointsSecondPassageEnAttente() {
        return lignes.stream()
                .filter(ligne -> !ligne.passage2().faite())
                .filter(ligne -> !aujourdhui.isAfter(echeanceSecondPassage()))
                .count();
    }

    private Stream<CasePassage> cases() {
        return lignes.stream().flatMap(ligne -> Stream.of(ligne.passage1(), ligne.passage2()));
    }
}
