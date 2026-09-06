package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que ces cas gardent, et ce qu'ils ne peuvent pas garder.
///
/// [Selecteurs] n'a qu'une ligne, et son intérêt n'est pas ce qu'elle fait mais le fait qu'elle soit
/// **seule**. Cette unicité-là ne se prouve pas ici : elle se prouve par le garde
/// `scripts/adr/5307-designation-hors-fabrique.py`, qui compte les constructions ailleurs.
///
/// Ce qui se garde ici est le **contrat** de la fabrique : elle rend un porteur remplaçable, et non
/// un sélecteur nu. C'est ce que les douze appelants gardent en champ `final` et exposent à leurs
/// tests, et le jour où la fabrique choisira entre deux dispositifs, c'est cette propriété-là qui
/// devra survivre.
class SelecteursTest {

    /// La fabrique d'une installation neuve : le défaut du produit, sans rien monter.
    private Selecteurs fabrique() {
        return SelecteursDeTest.auDefaut();
    }

    /// Le dispositif que la fabrique désignerait **maintenant**.
    private static SelecteurFichier dispositifDe(Selecteurs fabrique) {
        return ((SelecteurSelonLaPreference) fabrique.pour(() -> null).delegue()).dispositifCourant();
    }

    @Test
    @DisplayName("la fabrique rend un porteur REMPLAÇABLE, pas un sélecteur nu")
    void la_fabrique_rend_un_porteur_remplacable() {
        // Sans cette propriété, les douze écrans perdraient leur couture de testabilité, et le geste
        // qui commence par désigner un fichier redeviendrait injouable en test - le défaut même qui a
        // fait naître le port.
        SelecteurFichierModifiable porteur = fabrique().pour(() -> null);

        assertThat(porteur).isNotNull();
    }

    @Test
    @DisplayName("le porteur rendu accepte un double, et c'est lui qui répond ensuite")
    void le_porteur_rendu_accepte_un_double() {
        SelecteurFichierModifiable porteur = fabrique().pour(() -> null);
        Path attendu = Path.of("/tmp/vc-essai");

        porteur.definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(attendu);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(attendu);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                return Optional.of(attendu);
            }
        });

        // Les trois gestes du contrat, et pas seulement le premier : un porteur qui n'en délègue que
        // deux laisserait un geste sur le dispositif réel, donc figerait un test headless.
        assertThat(porteur.choisirDossier("titre", Optional.empty())).contains(attendu);
        assertThat(porteur.choisirFichier("titre", Optional.empty(), FiltreFichier.csv()))
                .contains(attendu);
        assertThat(porteur.enregistrerFichier("titre", "nom.csv", FiltreFichier.csv()))
                .contains(attendu);
    }

    @Test
    @DisplayName("SANS réglage écrit, c'est le dialogue du SYSTÈME - le défaut d'une installation neuve")
    void le_defaut_est_le_dialogue_du_systeme() {
        // La contrepartie assumée du changement : ce que voit l'utilisateur ordinaire ne change pas.
        // Le défaut tient par construction, `DEFAUT_DIALOGUE_DE_L_APPLICATION` valant `false`, et non
        // par une ligne qu'on pourrait oublier d'écrire.
        SelecteurFichier dispositif = dispositifDe(fabrique());

        assertThat(dispositif).isInstanceOf(SelecteurFichierJavaFx.class);
    }

    @Test
    @DisplayName("réglage POSÉ, c'est le dialogue de l'application")
    void le_reglage_pose_donne_le_dialogue_de_l_application() {
        SelecteurFichier dispositif = dispositifDe(SelecteursDeTest.avecLeDialogueDeLApplication());

        assertThat(dispositif).isInstanceOf(SelecteurFichierEnFenetre.class);
    }

    @Test
    @DisplayName("le réglage est relu à CHAQUE désignation, et non figé à la construction de l'écran")
    void le_reglage_est_relu_a_chaque_designation() {
        // Le témoin du défaut que ce lot a d'abord écrit. Les écrans appellent la fabrique dans leur
        // CONSTRUCTEUR, pour garder le porteur en champ `final` : résoudre le dispositif là aurait
        // rendu l'interrupteur sans effet jusqu'au redémarrage. Avec un dispositif figé, ce cas
        // rougit sur la seconde assertion.
        boolean[] choisi = {false};
        SelecteurFichierModifiable porteur = new Selecteurs(() -> choisi[0]).pour(() -> null);
        SelecteurSelonLaPreference selon = (SelecteurSelonLaPreference) porteur.delegue();

        assertThat(selon.dispositifCourant()).isInstanceOf(SelecteurFichierJavaFx.class);

        choisi[0] = true;

        assertThat(selon.dispositifCourant()).isInstanceOf(SelecteurFichierEnFenetre.class);
    }
}
