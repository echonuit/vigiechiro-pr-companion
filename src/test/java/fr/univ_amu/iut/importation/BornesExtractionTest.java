package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.importation.model.BornesExtraction;
import fr.univ_amu.iut.importation.model.InventaireArchive;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les bornes elles-mêmes (#2732), éprouvées sans archive : ce sont des fonctions pures, et les
/// questions qu'elles posent (une limite est-elle incluse ? la marge compte-t-elle ?) se lisent mieux
/// sur des chiffres que sur des fichiers zip.
///
/// Survivants PIT **assumés**, deux bornes que rien ne distingue en pratique : dans
/// `InventaireArchive.lire`, le `>` qui retient la plus grosse entrée remplacé par `>=` (à taille
/// égale, le refus nommerait la dernière plutôt que la première, les deux sont justes) ; et dans
/// `nomCourt`, le `< 0` remplacé par `<= 0`, qui ne diverge que sur un nom d'entrée commençant par
/// « / », invalide en pratique - même exemption que `EcrivainZip.nomCourt`.
///
/// Les autres survivants étaient de **vrais trous**, tous sur ce que le code promet sans le prouver :
/// la surcharge par propriété système, que le message de refus recommande à l'utilisateur ; la marge
/// disque, jamais franchie par les tests qui la mettaient à zéro ; le repli quand l'archive n'annonce
/// aucune taille ; le fait que les dossiers ne comptent pas comme des fichiers ; et le nom court, dont
/// l'assertion tolérait qu'il garde un bout de chemin.
class BornesExtractionTest {

    private static final Path PEU_IMPORTE = Path.of(".");

    private static final String[] PROPRIETES = {
        "vigiechiro.import.zip.max-entrees",
        "vigiechiro.import.zip.max-octets-par-entree",
        "vigiechiro.import.zip.max-octets-total",
        "vigiechiro.import.zip.marge-disque-octets"
    };

    @AfterEach
    void nettoyerLesProprietes() {
        for (String propriete : PROPRIETES) {
            System.clearProperty(propriete);
        }
    }

    @Test
    @DisplayName("Les bornes sont inclusives : pile à la limite, l'archive passe")
    void les_bornes_sont_inclusives() {
        BornesExtraction bornes = new BornesExtraction(3, 1000, 2000, 0, dossier -> 2000);
        // Trois entrées pour un maximum de trois, la plus grosse à 1000 pour un maximum de 1000, un total
        // de 2000 pour un maximum de 2000, et un disque qui offre exactement le nécessaire.
        InventaireArchive pileALaLimite = new InventaireArchive(3, 2000, 1000, "gros.wav");

        assertThatCode(() -> bornes.verifierAvantExtraction(pileALaLimite, PEU_IMPORTE))
                .as("une limite refuse ce qui la DÉPASSE, pas ce qui l'atteint")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Une archive qui écrit exactement ce qu'elle annonce n'est pas interrompue")
    void ecrire_exactement_ce_qui_est_annonce_passe() {
        BornesExtraction bornes = BornesExtraction.parDefaut();
        InventaireArchive inventaire = new InventaireArchive(1, 12_582_912, 12_582_912, "gros.wav");

        // Le cas nominal de toute archive bien formée : la moindre sévérité de plus refuserait tout.
        assertThatCode(() -> bornes.exigerCumulSousLePlafond(12_582_912, inventaire))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> bornes.exigerCumulSousLePlafond(12_582_913, inventaire))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("Archive qui n'annonce aucune taille : il reste la borne absolue, pas un plafond à zéro")
    void sans_taille_annoncee_la_borne_absolue_prend_le_relais() {
        // Une archive dont le répertoire central ne porte aucune taille (elles valent alors -1, comptées
        // 0) : le second garde n'a rien à quoi la confronter. Se rabattre sur zéro refuserait le premier
        // octet venu ; c'est la borne absolue qui prend le relais.
        BornesExtraction bornes = new BornesExtraction(100, Long.MAX_VALUE, 10_000_000, 0, d -> Long.MAX_VALUE);
        InventaireArchive muette = new InventaireArchive(1, 0, 0, "inconnu.wav");

        assertThatCode(() -> bornes.exigerCumulSousLePlafond(1_000_000, muette)).doesNotThrowAnyException();
        assertThatThrownBy(() -> bornes.exigerCumulSousLePlafond(10_000_001, muette))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("La marge disque compte : un volume qui offre le strict nécessaire est refusé")
    void la_marge_disque_compte() {
        BornesExtraction bornes = new BornesExtraction(100, Long.MAX_VALUE, Long.MAX_VALUE, 500, d -> 1000);
        InventaireArchive inventaire = new InventaireArchive(1, 1000, 1000, "a.wav");

        // Mille octets annoncés, mille octets libres : sans la marge, l'extraction partirait et laisserait
        // un volume exactement plein, ce que la marge existe pour éviter.
        assertThatThrownBy(() -> bornes.verifierAvantExtraction(inventaire, PEU_IMPORTE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Espace disque insuffisant");
    }

    @Test
    @DisplayName("Une archive vide n'est pas suspecte : elle ne demande rien")
    void archive_vide_acceptee() {
        InventaireArchive vide = new InventaireArchive(0, 0, 0, "");

        assertThatCode(() -> BornesExtraction.parDefaut().verifierAvantExtraction(vide, PEU_IMPORTE))
                .as("aucune borne n'est franchie par une archive qui n'annonce rien")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Aucun refus n'est tronqué par le bandeau, même au pire cas (#2076)")
    void les_refus_tiennent_dans_le_bandeau() {
        // Le bandeau borne à 240 caractères ce qui vient d'ailleurs et colle « … (détail dans le journal) ».
        // Or ces refus mettent en FIN de phrase ce qu'il faut faire : tronqués, ils ne gardent que le
        // reproche. Le pire cas : un chemin interne d'archive à rallonge et des volumes à deux chiffres.
        String nomInterminable = "Sauvegarde du 22 avril 2026/Cartes SD rapatriees/Site Z1 - carre 640380/"
                + "Passage 2 - nuit du 22 au 23/Enregistreur Passive Recorder n02/bruts non transformes/"
                + "PaRecPR1925492_20260422_203922_seq_0001_bis.wav";
        BornesExtraction serrees = new BornesExtraction(1, 1, 1, 1, dossier -> 1);
        InventaireArchive enorme = new InventaireArchive(999_999, 900_000_000_000L, 800_000_000_000L, nomInterminable);

        for (int borne = 0; borne < 4; borne++) {
            String rendu = texteDuRefus(serrees, enorme, borne);
            assertThat(rendu).as("refus n°%d rendu par le bandeau", borne).doesNotContain("détail dans le journal");
        }
    }

    /// Rend le refus tel que le bandeau l'affichera, en désactivant les bornes une à une pour atteindre
    /// la suivante : c'est le seul moyen de voir chaque message, puisque le premier franchissement
    /// interrompt la vérification.
    private static String texteDuRefus(BornesExtraction serrees, InventaireArchive inventaire, int borneVisee) {
        BornesExtraction bornes = new BornesExtraction(
                borneVisee > 0 ? Integer.MAX_VALUE : serrees.maxEntrees(),
                borneVisee > 1 ? Long.MAX_VALUE : serrees.maxOctetsParEntree(),
                borneVisee > 2 ? Long.MAX_VALUE : serrees.maxOctetsTotal(),
                serrees.margeDisqueOctets(),
                serrees.espaceDisque());
        try {
            bornes.verifierAvantExtraction(inventaire, PEU_IMPORTE);
            bornes.exigerCumulSousLePlafond(Long.MAX_VALUE, inventaire);
            throw new IllegalStateException("aucun refus n'a été levé pour la borne " + borneVisee);
        } catch (RuntimeException | java.io.IOException refus) {
            return RetourOperation.erreur(refus).texte();
        }
    }

    @Test
    @DisplayName("Chaque borne se surcharge par propriété système, comme le dit le message de refus")
    void surcharge_par_propriete_systeme() {
        // Le refus promet « relancez avec -Dvigiechiro.import.zip.max-entrees=<valeur> » : si la surcharge
        // ne marchait pas, le message enverrait l'utilisateur dans le mur.
        System.setProperty("vigiechiro.import.zip.max-entrees", "7");
        System.setProperty("vigiechiro.import.zip.max-octets-par-entree", "11");
        System.setProperty("vigiechiro.import.zip.max-octets-total", "13");
        System.setProperty("vigiechiro.import.zip.marge-disque-octets", "19");

        BornesExtraction bornes = BornesExtraction.parDefaut();

        assertThat(bornes.maxEntrees()).isEqualTo(7);
        assertThat(bornes.maxOctetsParEntree()).isEqualTo(11);
        assertThat(bornes.maxOctetsTotal()).isEqualTo(13);
        assertThat(bornes.margeDisqueOctets()).isEqualTo(19);
    }

    @Test
    @DisplayName("Sans surcharge, les défauts laissent passer une vraie nuit de terrain")
    void les_defauts_laissent_passer_une_vraie_nuit() {
        // Ordre de grandeur d'une nuit réelle : quelques milliers de fichiers, une dizaine de Go. Ce test
        // est le garde-fou contre un resserrement des défauts.
        InventaireArchive nuitReelle = new InventaireArchive(2_000, 10_000_000_000L, 20_000_000, "PaRec.wav");
        BornesExtraction bornes = new BornesExtraction(
                BornesExtraction.parDefaut().maxEntrees(),
                BornesExtraction.parDefaut().maxOctetsParEntree(),
                BornesExtraction.parDefaut().maxOctetsTotal(),
                BornesExtraction.parDefaut().margeDisqueOctets(),
                dossier -> 500_000_000_000L);

        assertThatCode(() -> bornes.verifierAvantExtraction(nuitReelle, PEU_IMPORTE))
                .doesNotThrowAnyException();
    }
}
