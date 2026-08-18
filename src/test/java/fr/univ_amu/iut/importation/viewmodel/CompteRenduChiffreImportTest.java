package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.model.VolumesImport;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Traduction d'un import en compte rendu chiffré (#2358).
///
/// Ce qui se vérifie ici est la **fidélité** : le panneau dessine ce que ce modèle dit, donc une
/// quantité fausse produirait une barre fausse, avec l'autorité du visuel. Les tests portent donc sur
/// les chiffres et sur l'exhaustivité de la ventilation, pas sur la mise en forme.
class CompteRenduChiffreImportTest {

    private static final String SERIE = "1925492";

    /// Un import type : 3 importés, 1 ignoré, 2 rejetés pour la même raison, 1 pour une autre.
    private static RapportImport rapportType() {
        return new RapportImport(List.of(
                ligne("a.wav", StatutImportFichier.IMPORTE, "2 séquence(s)"),
                ligne("b.wav", StatutImportFichier.IMPORTE, "2 séquence(s)"),
                ligne("c.wav", StatutImportFichier.IMPORTE, "1 séquence(s)"),
                ligne("notes.txt", StatutImportFichier.IGNORE, ""),
                ligne("d.wav", StatutImportFichier.REJETE, "Original illisible (en-tête WAV) : d.wav"),
                ligne("e.wav", StatutImportFichier.REJETE, "Original illisible (en-tête WAV) : e.wav"),
                ligne(
                        "f.wav",
                        StatutImportFichier.REJETE,
                        "Fréquence d'acquisition 384001 Hz non divisible par 10 : f.wav")));
    }

    private static LigneRapport ligne(String nom, StatutImportFichier statut, String detail) {
        return new LigneRapport(nom, statut, detail);
    }

    private static ResultatImport resultat(RapportImport rapport, VolumesImport volumes, List<String> anomalies) {
        return new ResultatImport(
                passage("2026-04-22"),
                new SessionDEnregistrement(1L, "/ws/Car640380-2026-Pass2-A1", 0L, 0L, 1L),
                SERIE,
                3,
                5,
                anomalies,
                rapport,
                volumes);
    }

    private static Passage passage(String date) {
        return new Passage(
                1L,
                2,
                2026,
                date,
                "20:25",
                "07:47",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                7L,
                SERIE,
                null);
    }

    private static CompteRenduChiffre rendu() {
        return CompteRenduChiffreImport.de(
                resultat(rapportType(), new VolumesImport(5_000_000_000L, 5_000_000_000L, 1_800_000_000L), List.of()),
                List.of(new Action("Ouvrir le passage", true, () -> {})));
    }

    @Nested
    @DisplayName("Le verdict en tête")
    class Verdict {

        @Test
        @DisplayName("Le résultat compare les importés au total des fichiers de la source")
        void resultat_compare_au_total() {
            assertThat(rendu().resultat()).isEqualTo("3 / 7 importés");
        }

        @Test
        @DisplayName("Tout passé : le résultat ne montre pas un écart qui n'existe pas")
        void resultat_sans_ecart() {
            RapportImport toutPasse = new RapportImport(List.of(
                    ligne("a.wav", StatutImportFichier.IMPORTE, ""), ligne("b.wav", StatutImportFichier.IMPORTE, "")));

            CompteRenduChiffre rendu =
                    CompteRenduChiffreImport.de(resultat(toutPasse, VolumesImport.AUCUN, List.of()), List.of());

            assertThat(rendu.resultat()).isEqualTo("2 importés");
            assertThat(rendu.severite()).isEqualTo(Severite.SUCCES);
        }

        @Test
        @DisplayName("Un rejet n'est pas une erreur d'import, c'est un avertissement : des fichiers manquent en base")
        void rejets_donnent_un_avertissement() {
            assertThat(rendu().severite()).isEqualTo(Severite.AVERTISSEMENT);
        }

        @Test
        @DisplayName("Une anomalie du journal du capteur suffit à nuancer un import sans rejet")
        void anomalie_seule_nuance_le_verdict() {
            RapportImport toutPasse = new RapportImport(List.of(ligne("a.wav", StatutImportFichier.IMPORTE, "")));

            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(
                    resultat(toutPasse, VolumesImport.AUCUN, List.of("Sonde de température absente")), List.of());

            assertThat(rendu.severite()).isEqualTo(Severite.AVERTISSEMENT);
            assertThat(rendu.textesDesAvertissements()).contains("Sonde de température absente");
        }

        @Test
        @DisplayName("Le titre nomme la nuit, le carré et le point, relus du dossier de session")
        void titre_nomme_la_nuit_et_le_point() {
            assertThat(rendu().titre()).isEqualTo("Import terminé - nuit du 22/04/2026, carré 640380 · A1");
        }

        @Test
        @DisplayName("Sans agrégat à nommer, le titre se réduit au lieu de casser")
        void titre_sans_agregat() {
            ResultatImport sansPassage =
                    new ResultatImport(null, null, SERIE, 1, 1, List.of(), rapportType(), VolumesImport.AUCUN);

            assertThat(CompteRenduChiffreImport.de(sansPassage, List.of()).titre())
                    .isEqualTo("Import terminé");
        }
    }

    @Nested
    @DisplayName("La ventilation est exhaustive")
    class Ventilation {

        @Test
        @DisplayName("La somme des segments fait le total, sans « autres » silencieux")
        void somme_des_segments_egale_le_total() {
            var ventilation = rendu().ventilation();

            assertThat(ventilation.total()).isEqualTo(7);
            assertThat(ventilation.segments().stream()
                            .mapToLong(Segment::quantite)
                            .sum())
                    .as("le record refuserait un reliquat sans nom ; on vérifie que l'import ne lui en donne pas")
                    .isEqualTo(7);
            assertThat(ventilation.segments())
                    .extracting(Segment::libelle)
                    .containsExactly("Importés", "Ignorés", "Rejetés");
        }

        @Test
        @DisplayName("Un statut sans fichier ne se déclare pas : « 0 ignoré » serait du bruit")
        void statut_vide_absent() {
            RapportImport sansIgnore = new RapportImport(List.of(
                    ligne("a.wav", StatutImportFichier.IMPORTE, ""),
                    ligne("d.wav", StatutImportFichier.REJETE, "Original illisible : d.wav")));

            var ventilation = CompteRenduChiffreImport.de(
                            resultat(sansIgnore, VolumesImport.AUCUN, List.of()), List.of())
                    .ventilation();

            assertThat(ventilation.segments()).extracting(Segment::libelle).containsExactly("Importés", "Rejetés");
        }

        @Test
        @DisplayName("Une source sans aucun fichier n'a pas de ventilation à montrer")
        void source_vide_sans_ventilation() {
            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(
                    resultat(new RapportImport(List.of()), VolumesImport.AUCUN, List.of()), List.of());

            assertThat(rendu.ventilation().estVide()).isTrue();
        }
    }

    @Nested
    @DisplayName("Les volumes lus et écrits")
    class Volumes {

        @Test
        @DisplayName("Deux barres : ce que la carte a donné, ce que le disque a pris")
        void deux_barres_a_echelle_commune() {
            List<Barre> volumes = rendu().volumes();

            assertThat(volumes).extracting(Barre::libelle).containsExactly("Lu sur la carte", "Écrit sur le disque");
            assertThat(volumes.get(0).total()).isEqualTo(5_000_000_000L);
            assertThat(volumes.get(1).total())
                    .as("l'écrit cumule les bruts conservés et les séquences")
                    .isEqualTo(6_800_000_000L);
            assertThat(volumes.get(1).segments())
                    .extracting(Segment::libelle)
                    .containsExactly("bruts conservés", "séquences");
        }

        @Test
        @DisplayName("Sans conservation, la barre écrite se réduit aux séquences")
        void sans_bruts_une_seule_part() {
            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(
                    resultat(rapportType(), new VolumesImport(5_000_000_000L, 0, 1_800_000_000L), List.of()),
                    List.of());

            assertThat(rendu.volumes().get(1).segments())
                    .as("une part « bruts conservés » à zéro se lirait comme une archive vide, non comme son absence")
                    .extracting(Segment::libelle)
                    .containsExactly("séquences");
        }

        @Test
        @DisplayName("Rien de mesuré : le bloc des volumes est tu, pas dessiné à zéro")
        void volumes_non_mesures_absents() {
            CompteRenduChiffre rendu =
                    CompteRenduChiffreImport.de(resultat(rapportType(), VolumesImport.AUCUN, List.of()), List.of());

            assertThat(rendu.volumes()).isEmpty();
        }

        @Test
        @DisplayName("Les valeurs sont lisibles, pas des octets bruts")
        void valeurs_lisibles() {
            assertThat(rendu().volumes().get(0).segments().getFirst().valeurLisible())
                    .isEqualTo("5,0 Go");
        }
    }

    @Nested
    @DisplayName("Les motifs de rejet")
    class Motifs {

        @Test
        @DisplayName("Un motif par raison, portant les fichiers concernés")
        void un_motif_par_raison() {
            List<Motif> motifs = rendu().motifs();

            assertThat(motifs).hasSize(2);
            assertThat(motifs.get(0).sujets()).containsExactly("d.wav", "e.wav");
            assertThat(motifs.get(1).sujets()).containsExactly("f.wav");
        }

        @Test
        @DisplayName("Le nom du fichier est retiré de la raison : sinon chaque fichier serait son propre motif")
        void la_raison_ne_reprend_pas_le_nom_du_fichier() {
            List<Motif> motifs = rendu().motifs();

            // Le moteur produit « Original illisible (en-tête WAV) : d.wav ». Sans retaille, d.wav et e.wav
            // auraient deux raisons distinctes, donc deux motifs d'un fichier chacun : aucun regroupement.
            assertThat(motifs.get(0).libelle()).isEqualTo("fichier(s) : Original illisible (en-tête WAV)");
            assertThat(motifs.get(0).libelle()).doesNotContain(".wav");
            assertThat(motifs.get(0).compte()).isEqualTo(2);
        }

        @Test
        @DisplayName("Une raison sans nom de fichier à retirer passe telle quelle")
        void raison_sans_suffixe_intacte() {
            RapportImport rapport = new RapportImport(
                    List.of(ligne("d.wav", StatutImportFichier.REJETE, "OriginalIllisibleException")));

            List<Motif> motifs = CompteRenduChiffreImport.de(
                            resultat(rapport, VolumesImport.AUCUN, List.of()), List.of())
                    .motifs();

            assertThat(motifs)
                    .singleElement()
                    .extracting(Motif::libelle)
                    .isEqualTo("fichier(s) : OriginalIllisibleException");
        }

        @Test
        @DisplayName("Une raison vide devient un motif nommé, jamais un motif muet")
        void raison_vide_nommee() {
            RapportImport rapport = new RapportImport(List.of(ligne("d.wav", StatutImportFichier.REJETE, "")));

            assertThat(CompteRenduChiffreImport.de(resultat(rapport, VolumesImport.AUCUN, List.of()), List.of())
                            .motifs())
                    .singleElement()
                    .extracting(Motif::libelle)
                    .isEqualTo("fichier(s) : raison non précisée");
        }

        @Test
        @DisplayName("Sans rejet, aucun motif")
        void sans_rejet_aucun_motif() {
            RapportImport toutPasse = new RapportImport(List.of(ligne("a.wav", StatutImportFichier.IMPORTE, "")));

            assertThat(CompteRenduChiffreImport.de(resultat(toutPasse, VolumesImport.AUCUN, List.of()), List.of())
                            .motifs())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Ce qui reste vrai après l'import")
    class Avertissements {

        @Test
        @DisplayName("Le doublon de nuit assumé est rappelé, sans déverser la liste des passages")
        void doublon_de_nuit_rappele() {
            RapportImport avecDoublon = new RapportImport(
                    List.of(ligne("a.wav", StatutImportFichier.IMPORTE, "")),
                    List.of(
                            new PassageExistant(1, 2026, "640380", "A1"),
                            new PassageExistant(2, 2026, "640380", "A1")));

            CompteRenduChiffre rendu =
                    CompteRenduChiffreImport.de(resultat(avecDoublon, VolumesImport.AUCUN, List.of()), List.of());

            assertThat(rendu.textesDesAvertissements())
                    .singleElement()
                    .asString()
                    .contains("déjà importée")
                    .contains("2 passage(s)");
            assertThat(rendu.severite()).isEqualTo(Severite.AVERTISSEMENT);
        }

        @Test
        @DisplayName("Les passages déjà présents sont nommés dans un motif, pas déversés dans l'avertissement")
        void passages_deja_presents_en_motif() {
            RapportImport avecDoublon = new RapportImport(
                    List.of(ligne("a.wav", StatutImportFichier.IMPORTE, "")),
                    List.of(
                            new PassageExistant(1, 2026, "640380", "A1"),
                            new PassageExistant(2, 2026, "640380", "A1")));

            CompteRenduChiffre rendu =
                    CompteRenduChiffreImport.de(resultat(avecDoublon, VolumesImport.AUCUN, List.of()), List.of());

            // Les verser dans le texte de l'avertissement les rendrait de longueur non bornée dans un
            // encart d'une ligne, ce que l'ADR 0031 a soldé. En motif, ils se comptent puis s'ouvrent.
            assertThat(rendu.motifs()).singleElement().satisfies(motif -> {
                assertThat(motif.libelle()).isEqualTo("passage(s) déjà présent(s) pour cette nuit");
                assertThat(motif.sujets())
                        .containsExactly(
                                "n° 1 (2026) au carré 640380, point A1", "n° 2 (2026) au carré 640380, point A1");
            });
            assertThat(rendu.textesDesAvertissements())
                    .singleElement()
                    .asString()
                    .as("l'avertissement dénombre, il n'énumère pas")
                    .doesNotContain("carré 640380");
        }
    }

    @Nested
    @DisplayName("Un import multi-nuits")
    class MultiNuits {

        private static ResultatImportMultiNuits troisNuits() {
            return new ResultatImportMultiNuits(
                    List.of(nuit("2026-04-22", 2, 1), nuit("2026-04-23", 3, 0), nuit("2026-04-24", 1, 2)));
        }

        static ResultatImport nuit(String date, int importes, int rejetes) {
            List<LigneRapport> lignes = new java.util.ArrayList<>();
            for (int i = 0; i < importes; i++) {
                lignes.add(ligne(date + "-ok-" + i + ".wav", StatutImportFichier.IMPORTE, ""));
            }
            for (int i = 0; i < rejetes; i++) {
                String nom = date + "-ko-" + i + ".wav";
                lignes.add(ligne(nom, StatutImportFichier.REJETE, "Original illisible (en-tête WAV) : " + nom));
            }
            return new ResultatImport(
                    passage(date),
                    new SessionDEnregistrement(1L, "/ws/Car640380-2026-Pass2-A1", 0L, 0L, 1L),
                    SERIE,
                    importes,
                    importes,
                    List.of(),
                    new RapportImport(lignes),
                    new VolumesImport(1_000_000_000L, 0, 400_000_000L));
        }

        @Test
        @DisplayName("Les chiffres portent sur toutes les nuits, pas sur la première")
        void agrege_toutes_les_nuits() {
            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(troisNuits(), List.of());

            assertThat(rendu.ventilation().total())
                    .as("6 importés + 3 rejetés sur les trois nuits")
                    .isEqualTo(9);
            assertThat(rendu.resultat()).isEqualTo("6 / 9 importés");
        }

        @Test
        @DisplayName("Les rejets des nuits suivantes ne sont pas tus : ils se regroupent par raison")
        void rejets_de_toutes_les_nuits() {
            List<Motif> motifs =
                    CompteRenduChiffreImport.de(troisNuits(), List.of()).motifs();

            assertThat(motifs)
                    .as("une seule raison, mais venue de deux nuits différentes")
                    .singleElement()
                    .extracting(Motif::compte)
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("Les volumes se cumulent : trois nuits pèsent trois fois")
        void volumes_cumules() {
            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(troisNuits(), List.of());

            assertThat(rendu.volumes().get(0).total()).isEqualTo(3_000_000_000L);
            assertThat(rendu.volumes().get(1).total()).isEqualTo(1_200_000_000L);
        }

        @Test
        @DisplayName("Le titre dit la plage couverte, pas seulement le nombre de passages")
        void titre_dit_la_plage() {
            assertThat(CompteRenduChiffreImport.de(troisNuits(), List.of()).titre())
                    .isEqualTo("Import terminé - 3 nuits, du 22/04/2026 au 24/04/2026");
        }

        @Test
        @DisplayName("Une seule nuit incluse : le titre est celui d'un import mono-nuit")
        void une_seule_nuit_titre_mono() {
            var uneNuit = new ResultatImportMultiNuits(List.of(nuit("2026-04-22", 2, 0)));

            assertThat(CompteRenduChiffreImport.de(uneNuit, List.of()).titre())
                    .isEqualTo("Import terminé - nuit du 22/04/2026, carré 640380 · A1");
        }
    }

    @Nested
    @DisplayName("#1488 : ce que le rapport ne sait pas")
    class ContexteApres {

        private static final String MELANGE = "Ce dossier mélangeait plusieurs enregistreurs";

        @Test
        @DisplayName("Les avertissements d'inspection encore vrais sont rappelés, en tête")
        void avertissements_encore_vrais_rappeles() {
            var contexte = new CompteRenduChiffreImport.ContexteApresImport(
                    List.of(MELANGE + " (1925492, 1648011) : le passage créé peut contenir des enregistrements"
                            + " d'un autre appareil."),
                    0);

            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(
                    resultat(rapportType(), VolumesImport.AUCUN, List.of("Sonde absente")), List.of(), contexte);

            // En tête des autres : importer ne résout pas un mélange d'enregistreurs, cela le grave dans
            // un passage - c'est plus grave qu'une sonde absente.
            assertThat(rendu.textesDesAvertissements()).first().asString().startsWith(MELANGE);
            assertThat(rendu.textesDesAvertissements()).contains("Sonde absente");
        }

        @Test
        @DisplayName("La participation créée se dit : une écriture distante ne se découvre pas ailleurs")
        void participation_creee_annoncee() {
            ResultatImport avecParticipation =
                    resultat(rapportType(), VolumesImport.AUCUN, List.of()).avecParticipationCreee();

            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(avecParticipation, List.of());

            assertThat(rendu.textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte)
                            .contains("Participation créée sur Vigie-Chiro")
                            .contains("le dépôt la réutilisera"));
        }

        @Test
        @DisplayName("#3473 : l'annonce dit aussi ce qu'il reste à compléter sur le portail")
        void participation_creee_dit_ce_qui_reste() {
            // Le retour de terrain : « je trouverais utile d'avoir un petit message nous disant
            // "passage créé sur vigichiro ; pensez à remplir les informations complémentaires !" ».
            // Le message annonçait la création, donc un fait accompli, ce qui se lit comme « c'est
            // fait » alors que la fiche web reste à compléter (météo, matériel, commentaires).
            ResultatImport avecParticipation =
                    resultat(rapportType(), VolumesImport.AUCUN, List.of()).avecParticipationCreee();

            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(avecParticipation, List.of());

            assertThat(rendu.textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte).contains("compléter"));
        }

        @Test
        @DisplayName("#3473 : la forme plurielle le dit aussi, c'est celle d'un import multi-nuits")
        void participations_creees_disent_ce_qui_reste() {
            // La forme qu'on oublie : elle ne sort que d'un import découpé en plusieurs nuits, et
            // c'est justement le cas d'une carte laissée plusieurs jours sur le terrain.
            var nuits = new ResultatImportMultiNuits(List.of(
                    MultiNuits.nuit("2026-04-22", 2, 0).avecParticipationCreee(),
                    MultiNuits.nuit("2026-04-23", 1, 0).avecParticipationCreee()));

            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(nuits, List.of());

            assertThat(rendu.textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte).contains("compléter"));
        }

        @Test
        @DisplayName("Sans participation, rien n'est annoncé : il n'y a rien à dire")
        void sans_participation_rien_a_dire() {
            CompteRenduChiffre rendu =
                    CompteRenduChiffreImport.de(resultat(rapportType(), VolumesImport.AUCUN, List.of()), List.of());

            assertThat(rendu.textesDesAvertissements()).noneMatch(texte -> texte.contains("Vigie-Chiro"));
        }

        @Test
        @DisplayName("En multi-nuits, les participations se comptent nuit par nuit")
        void participations_comptees_par_nuit() {
            var nuits = new ResultatImportMultiNuits(List.of(
                    MultiNuits.nuit("2026-04-22", 2, 0).avecParticipationCreee(),
                    MultiNuits.nuit("2026-04-23", 1, 0).avecParticipationCreee(),
                    MultiNuits.nuit("2026-04-24", 1, 0)));

            CompteRenduChiffre rendu = CompteRenduChiffreImport.de(nuits, List.of());

            // Deux sur trois : annoncer « 3 participations » mentirait sur la nuit restée locale.
            assertThat(rendu.textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte).startsWith("2 participations créées sur Vigie-Chiro"));
        }
    }

    @Test
    @DisplayName("Les actions viennent de l'écran : le compte rendu ne décide pas où mènent ses boutons")
    void les_actions_viennent_de_l_ecran() {
        List<Action> actions = List.of(new Action("Ouvrir le passage", true, () -> {}));

        assertThat(CompteRenduChiffreImport.de(resultat(rapportType(), VolumesImport.AUCUN, List.of()), actions)
                        .actions())
                .isEqualTo(actions);
    }
}
