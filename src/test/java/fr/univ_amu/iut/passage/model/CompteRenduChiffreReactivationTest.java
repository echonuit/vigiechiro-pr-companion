package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Traduction d'une réactivation en compte rendu chiffré (#2358).
///
/// Ce qui compte ici : la **proportion est juste**. « 10 séquences réactivées » ne dit pas si la nuit est
/// exploitable ; la barre le dit, donc une quantité fausse tromperait avec l'autorité du visuel.
class CompteRenduChiffreReactivationTest {

    /// Une réactivation partielle : 10 réactivées sur 14 attendues, 4 manquantes pour deux motifs.
    private static RapportReactivation partielle() {
        return new RapportReactivation(
                10,
                0,
                4,
                0,
                NiveauConfiance.CERTITUDE,
                List.of(),
                new DecompteAudio(10, 14),
                VoieReactivation.BRUTS,
                null,
                RapportAncrage.aucun(),
                List.of(
                        new AbsenceReactivation("a.wav", "enregistrement absent", 3),
                        new AbsenceReactivation("b.wav", "enregistrement absent", 2),
                        new AbsenceReactivation("c_001.wav", "tranche non régénérée", 1)));
    }

    private static RapportReactivation complete() {
        return new RapportReactivation(
                12,
                0,
                0,
                2,
                NiveauConfiance.CERTITUDE,
                List.of(),
                new DecompteAudio(14, 14),
                VoieReactivation.TRANSFORMES,
                null,
                RapportAncrage.aucun(),
                List.of());
    }

    private static CompteRenduChiffre rendu(RapportReactivation rapport) {
        return CompteRenduChiffreReactivation.de(rapport, List.of());
    }

    @Nested
    @DisplayName("Le verdict")
    class Verdict {

        @Test
        @DisplayName("Le résultat est le décompte : c'est lui qui dit si le passage est écoutable")
        void resultat_est_le_decompte() {
            assertThat(rendu(partielle()).resultat()).isEqualTo("10 / 14 séquences");
            assertThat(rendu(complete()).resultat())
                    .as("complet, l'écart n'existe pas : l'afficher ferait le chercher")
                    .isEqualTo("14 séquences présentes");
        }

        @Test
        @DisplayName("Incomplet est un avertissement, pas une erreur : la suite appelle l'utilisateur")
        void incomplet_est_un_avertissement() {
            assertThat(rendu(partielle()).severite()).isEqualTo(Severite.AVERTISSEMENT);
            assertThat(rendu(complete()).severite()).isEqualTo(Severite.SUCCES);
        }

        @Test
        @DisplayName("Un fichier au bon nom mais au mauvais audio est une erreur, même passage complet")
        void divergence_est_une_erreur() {
            // 13 réactivées + 1 manquante = 14 attendues : la séquence du fichier divergent reste
            // manquante, elle ne forme pas une catégorie de plus.
            RapportReactivation avecEcart = new RapportReactivation(
                    13,
                    1,
                    1,
                    0,
                    NiveauConfiance.CERTITUDE,
                    List.of(new EcartReactivation("d.wav", "empreinte différente")),
                    new DecompteAudio(13, 14),
                    VoieReactivation.TRANSFORMES,
                    null,
                    RapportAncrage.aucun(),
                    List.of());

            // Rebrancher un mauvais audio ferait pointer les observations sur le mauvais son : c'est plus
            // grave qu'une séquence absente, que l'utilisateur peut retrouver.
            assertThat(rendu(avecEcart).severite()).isEqualTo(Severite.ERREUR);
            assertThat(rendu(avecEcart).ventilation().segments())
                    .as("le divergent n'a pas de part : sa séquence est déjà comptée manquante")
                    .extracting(Segment::libelle)
                    .containsExactly("Réactivées", "Manquantes");
        }

        @Test
        @DisplayName("Le titre est celui du compte rendu textuel, pas une seconde rédaction")
        void titre_partage_avec_le_textuel() {
            assertThat(rendu(partielle()).titre()).isEqualTo("Réactivation partielle");
            assertThat(rendu(complete()).titre()).isEqualTo("Passage réactivé");
        }
    }

    @Nested
    @DisplayName("La ventilation")
    class Ventilation {

        @Test
        @DisplayName("Elle porte sur les séquences ATTENDUES, et la somme fait le total")
        void somme_egale_le_total_attendu() {
            var ventilation = rendu(partielle()).ventilation();

            assertThat(ventilation.total())
                    .as("le total est celui du décompte, pas la somme des parts")
                    .isEqualTo(14);
            assertThat(ventilation.segments().stream()
                            .mapToLong(Segment::quantite)
                            .sum())
                    .isEqualTo(14);
            assertThat(ventilation.segments()).extracting(Segment::libelle).containsExactly("Réactivées", "Manquantes");
        }

        @Test
        @DisplayName("Les déjà présentes ont leur part : elles n'ont pas été réactivées, elles étaient là")
        void deja_presentes_distinguees() {
            assertThat(rendu(complete()).ventilation().segments())
                    .extracting(Segment::libelle)
                    .containsExactly("Réactivées", "Déjà présentes");
        }

        @Test
        @DisplayName("Un décompte qui ne retombe pas juste nomme son reliquat plutôt que de le taire")
        void reliquat_nomme() {
            // 8 + 1 = 9 nommées pour 14 attendues : les 5 restantes ne sont dans aucun compteur. Un
            // « autres » silencieux masquerait exactement ce qu'on cherche ; la construction refuserait
            // même la ventilation. On les nomme.
            RapportReactivation incoherent = new RapportReactivation(
                    8,
                    0,
                    1,
                    0,
                    NiveauConfiance.CERTITUDE,
                    List.of(),
                    new DecompteAudio(8, 14),
                    VoieReactivation.BRUTS,
                    null,
                    RapportAncrage.aucun(),
                    List.of());

            assertThat(rendu(incoherent).ventilation().segments())
                    .extracting(Segment::libelle)
                    .contains("Non classées");
            assertThat(rendu(incoherent).ventilation().segments())
                    .filteredOn(segment -> "Non classées".equals(segment.libelle()))
                    .singleElement()
                    .extracting(Segment::quantite)
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("Un passage sans séquence attendue n'a pas de ventilation à montrer")
        void sans_sequence_attendue() {
            RapportReactivation vide = new RapportReactivation(
                    0,
                    0,
                    0,
                    0,
                    null,
                    List.of(),
                    new DecompteAudio(0, 0),
                    VoieReactivation.TRANSFORMES,
                    null,
                    RapportAncrage.aucun(),
                    List.of());

            assertThat(rendu(vide).ventilation().estVide()).isTrue();
        }
    }

    @Nested
    @DisplayName("Les motifs")
    class Motifs {

        @Test
        @DisplayName("Un motif par raison d'absence, les plus coûteuses d'abord")
        void motifs_par_raison() {
            List<Motif> motifs = rendu(partielle()).motifs();

            assertThat(motifs).hasSize(2);
            assertThat(motifs.get(0).libelle()).isEqualTo("fichier(s) : enregistrement absent");
            assertThat(motifs.get(0).sujets())
                    .as("le plus coûteux d'abord : c'est par lui qu'on commence à chercher")
                    .containsExactly("a.wav (3 séquences)", "b.wav (2 séquences)");
            assertThat(motifs.get(1).libelle()).isEqualTo("fichier(s) : tranche non régénérée");
            assertThat(motifs.get(1).sujets())
                    .as("une seule séquence : le compte n'a rien à préciser")
                    .containsExactly("c_001.wav");
        }

        @Test
        @DisplayName("Les fichiers refusés forment leur propre motif : jamais rebranchés en silence")
        void refuses_en_motif() {
            RapportReactivation avecEcart = new RapportReactivation(
                    13,
                    1,
                    1,
                    0,
                    NiveauConfiance.CERTITUDE,
                    List.of(new EcartReactivation("d.wav", "empreinte différente")),
                    new DecompteAudio(13, 14),
                    VoieReactivation.TRANSFORMES,
                    null,
                    RapportAncrage.aucun(),
                    List.of());

            assertThat(rendu(avecEcart).motifs()).singleElement().satisfies(motif -> {
                assertThat(motif.libelle()).contains("bon nom mais au mauvais audio");
                assertThat(motif.sujets()).containsExactly("d.wav - empreinte différente");
            });
        }

        @Test
        @DisplayName("Une réactivation complète n'a aucun motif à ouvrir")
        void complete_sans_motif() {
            assertThat(rendu(complete()).motifs()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ce qui reste vrai")
    class Avertissements {

        @Test
        @DisplayName("La conclusion sur l'écoutabilité y figure toujours")
        void conclusion_toujours_presente() {
            assertThat(rendu(complete()).textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte).contains("L'audio est de nouveau complet"));
            assertThat(rendu(partielle()).textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte).contains("L'audio reste incomplet"));
        }

        @Test
        @DisplayName("La voie « bruts » se dit : les séquences ont été régénérées, pas retrouvées")
        void voie_bruts_annoncee() {
            assertThat(rendu(partielle()).textesDesAvertissements())
                    .first()
                    .asString()
                    .contains("ne contenait que vos enregistrements bruts");
            assertThat(rendu(complete()).textesDesAvertissements())
                    .as("voie directe : rien à expliquer")
                    .noneMatch(texte -> texte.contains("enregistrements bruts"));
        }

        @Test
        @DisplayName("L'indice de concordance acoustique est rappelé, non bloquant")
        void indice_acoustique_rappele() {
            RapportReactivation avecIndice = new RapportReactivation(
                    14,
                    0,
                    0,
                    0,
                    NiveauConfiance.CERTITUDE,
                    List.of(),
                    new DecompteAudio(14, 14),
                    VoieReactivation.BRUTS,
                    new IndiceAcoustique(14, 12),
                    RapportAncrage.aucun(),
                    List.of());

            assertThat(rendu(avecIndice).textesDesAvertissements())
                    .anySatisfy(texte ->
                            assertThat(texte).contains("Concordance acoustique").contains("12 séquence(s) sur 14"));
        }

        @Test
        @DisplayName("Ce que la phase d'ancrage a rapatrié est repris tel quel, pas re-décrit")
        void rapatriement_repris_tel_quel() {
            RapportReactivation avecAncrage =
                    complete().avecRapatriement(new RapportAncrage("3 observations ancrées."));

            assertThat(rendu(avecAncrage).textesDesAvertissements()).contains("3 observations ancrées.");
        }
    }

    @Test
    @DisplayName("Les actions viennent de l'écran : le compte rendu ne décide pas où mènent ses boutons")
    void actions_viennent_de_l_ecran() {
        List<Action> actions = List.of(new Action("Écouter le passage", true, () -> {}));

        assertThat(CompteRenduChiffreReactivation.de(complete(), actions).actions())
                .isEqualTo(actions);
    }
}
