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

    /// Nombre de passages **à refaire** : la nuit existe mais son verdict la juge inexploitable.
    ///
    /// Avec [#passagesFaits] et [#passagesARealiser], forme une **ventilation exhaustive** des
    /// [#passagesAttendus] : la somme des trois vaut le total. C'est délibéré et contrôlé par un test.
    /// Un décompte qui ne ferme pas laisse l'observateur deviner où sont passés les manquants, ce que
    /// l'ancien résumé « 5/10 » faisait précisément.
    public long passagesARefaire() {
        return cases().filter(cas -> cas.presente() && cas.inexploitable()).count();
    }

    /// Nombre de passages **à réaliser** : aucune nuit enregistrée pour ce numéro. Dit « à réaliser » et
    /// non « à poser » : la fenêtre du second passage n'est pas forcément ouverte, et une consigne
    /// immédiate serait fausse la moitié de la saison.
    public long passagesARealiser() {
        return cases().filter(cas -> !cas.presente()).count();
    }

    /// Nombre de nuits **hors protocole** (opportunistes, #2525) réalisées sur les points suivis.
    ///
    /// Compté **à côté** du total, jamais dedans : ces nuits ont bien eu lieu, mais elles ne sont pas des
    /// passages attendus. Les fondre dans les [#passagesAttendus] referait l'erreur que la colonne
    /// « Hors protocole » corrige dans le tableau.
    public long nuitsHorsProtocole() {
        return lignes.stream().mapToLong(ligne -> ligne.horsProtocole().size()).sum();
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
        // Sortie anticipée plutôt qu'un second filtre : la fenêtre est close ou elle ne l'est pas, ce
        // n'est pas une propriété de la ligne. Filtrer chaque ligne sur une condition globale se lisait
        // comme si elle pouvait différer d'un point à l'autre.
        if (aujourdhui.isAfter(echeanceSecondPassage())) {
            return 0;
        }
        return lignes.stream().filter(ligne -> !ligne.passage2().faite()).count();
    }

    private Stream<CasePassage> cases() {
        return lignes.stream().flatMap(ligne -> Stream.of(ligne.passage1(), ligne.passage2()));
    }
}
