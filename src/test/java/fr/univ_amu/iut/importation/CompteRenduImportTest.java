package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Restitution d'un import, séparée en deux natures (ADR 0028 / 0031) : la phrase **bornée** de la barre
/// de statut, et le **compte rendu** extensible (doublon, ignorés, rejets, anomalies du journal).
///
/// Ce qui était éprouvé sur `RapportImport.avertissements()` l'est désormais ici : la mise en forme a
/// quitté le modèle, et surtout la vue, où elle n'était pas testable.
class CompteRenduImportTest {

    private static ResultatImport resultat(RapportImport rapport, List<String> anomalies) {
        return new ResultatImport(null, null, "1925492", 1, 3, anomalies, rapport);
    }

    private static RapportImport nominal() {
        return new RapportImport(List.of(new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)")));
    }

    @Test
    @DisplayName("statut : borné par construction, il ne dit que le volume traité")
    void statut_borne() {
        RapportImport charge = new RapportImport(
                List.of(
                        new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)"),
                        new LigneRapport("notes.txt", StatutImportFichier.IGNORE, ""),
                        new LigneRapport("b.wav", StatutImportFichier.REJETE, "en-tête illisible")),
                List.of(new PassageExistant(2, 2026, "640380", "Z1")));

        String phrase = CompteRenduImport.statut(EtatImport.TERMINE, resultat(charge, List.of("Tension faible")), null);

        // Le défaut d'origine : cette phrase se terminait par `rapport().avertissements()`, de longueur
        // non bornée, dans la barre de statut. Un doublon y déversait la liste des passages existants.
        assertThat(phrase).isEqualTo("✓ Import terminé : 3 séquence(s) produite(s) à partir de 1 original(aux).");
        assertThat(phrase).doesNotContain("Doublon", "ignoré", "rejeté", "Tension");
    }

    @Test
    @DisplayName("statut : annulation, et silence tant que l'import n'a pas abouti")
    void statut_selon_etat() {
        assertThat(CompteRenduImport.statut(EtatImport.ANNULE, null, null)).isEqualTo("Opération annulée.");
        assertThat(CompteRenduImport.statut(EtatImport.EN_COURS, resultat(nominal(), List.of()), null))
                .as("une barre de statut n'a pas à commenter l'attente")
                .isEmpty();
        assertThat(CompteRenduImport.statut(EtatImport.TERMINE, null, null)).isEmpty();
    }

    @Test
    @DisplayName("import nominal : rien à rapporter que le statut ne dise déjà")
    void compte_rendu_vide_si_nominal() {
        assertThat(CompteRenduImport.de(resultat(nominal(), List.of())).estVide())
                .isTrue();
        assertThat(CompteRenduImport.de((ResultatImport) null).estVide()).isTrue();
    }

    @Test
    @DisplayName("#214/#147 : chaque passage déjà présent est un détail, aucun n'est perdu")
    void doublon_de_nuit_porte_tous_les_passages() {
        RapportImport doublon = new RapportImport(
                List.of(new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)")),
                List.of(new PassageExistant(2, 2026, "640380", "Z1"), new PassageExistant(7, 2026, "130711", "Z4")));

        CompteRendu rendu = CompteRenduImport.de(resultat(doublon, List.of()));

        Constat constat = rendu.constats().getFirst();
        assertThat(constat.fait()).isEqualTo("Doublon : cette nuit était déjà importée.");
        assertThat(constat.severite()).isEqualTo(Severite.ERREUR);
        assertThat(constat.details())
                .extracting(Detail::sujet)
                .containsExactly("n° 2 au carré 640380", "n° 7 au carré 130711");
    }

    @Test
    @DisplayName("#155 : ignorés et rejets dénombrés, les rejets renvoyant à la liste de l'écran")
    void ignores_et_rejets() {
        RapportImport charge = new RapportImport(List.of(
                new LigneRapport("a.wav", StatutImportFichier.IMPORTE, "3 séquence(s)"),
                new LigneRapport("notes.txt", StatutImportFichier.IGNORE, ""),
                new LigneRapport("b.wav", StatutImportFichier.REJETE, "en-tête illisible"),
                new LigneRapport("c.wav", StatutImportFichier.REJETE, "durée nulle")));

        List<Constat> constats =
                CompteRenduImport.de(resultat(charge, List.of())).constats();

        assertThat(constats)
                .extracting(Constat::fait)
                .containsExactly(
                        "1 fichier(s) non pertinent(s) ignoré(s).", "2 fichier(s) rejeté(s) : détail ci-dessous.");
        // Les rejets n'emportent pas leurs détails : la ListView de l'écran les porte déjà, et une carte
        // SD réelle peut en produire des centaines. Les reprendre ici les montrerait deux fois.
        assertThat(constats.getLast().details()).isEmpty();
    }

    @Test
    @DisplayName("R19 : les anomalies du journal du capteur cessent d'être transportées sans être montrées")
    void anomalies_du_journal_enfin_visibles() {
        List<String> anomalies = List.of("Tension d'alimentation faible", "Horloge resynchronisée");

        CompteRendu rendu = CompteRenduImport.de(resultat(nominal(), anomalies));

        // `ResultatImport.anomalies` était alimenté depuis `journal.messagesAnomalies()`, documenté « pour
        // que l'IHM affiche un récapitulatif » - et aucune vue ne le lisait. Le champ existait, son
        // contenu se perdait entre le moteur d'import et l'écran.
        Constat constat = rendu.constats().getFirst();
        assertThat(constat.fait()).isEqualTo("2 anomalie(s) relevée(s) au journal du capteur.");
        assertThat(constat.details()).extracting(Detail::sujet).containsExactlyElementsOf(anomalies);
    }

    @Test
    @DisplayName("multi-nuits : les catégories sont agrégées sur TOUTES les nuits, pas sur la première")
    void multi_nuits_agrege() {
        ResultatImport premiere = resultat(
                new RapportImport(
                        List.of(new LigneRapport("a.wav", StatutImportFichier.REJETE, "en-tête illisible")),
                        List.of(new PassageExistant(2, 2026, "640380", "Z1"))),
                List.of("Tension faible"));
        ResultatImport seconde = resultat(
                new RapportImport(List.of(
                        new LigneRapport("b.wav", StatutImportFichier.REJETE, "durée nulle"),
                        new LigneRapport("notes.txt", StatutImportFichier.IGNORE, ""))),
                List.of());

        CompteRendu rendu = CompteRenduImport.de(new ResultatImportMultiNuits(List.of(premiere, seconde)));

        // Le ViewModel fait pointer `resultat` sur la PREMIÈRE nuit par compatibilité : rendre compte de
        // celle-là seule tairait le rejet de la seconde, alors qu'un import multi-nuits en produit plus.
        assertThat(rendu.constats())
                .extracting(Constat::fait)
                .contains("1 fichier(s) non pertinent(s) ignoré(s).", "2 fichier(s) rejeté(s) : détail ci-dessous.");
        assertThat(CompteRenduImport.de((ResultatImportMultiNuits) null).estVide())
                .isTrue();
    }
}
