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

    @Test
    @DisplayName("la fabrique rend un porteur REMPLAÇABLE, pas un sélecteur nu")
    void la_fabrique_rend_un_porteur_remplacable() {
        // Sans cette propriété, les douze écrans perdraient leur couture de testabilité, et le geste
        // qui commence par désigner un fichier redeviendrait injouable en test - le défaut même qui a
        // fait naître le port.
        SelecteurFichierModifiable porteur = Selecteurs.pour(() -> null);

        assertThat(porteur).isNotNull();
    }

    @Test
    @DisplayName("le porteur rendu accepte un double, et c'est lui qui répond ensuite")
    void le_porteur_rendu_accepte_un_double() {
        SelecteurFichierModifiable porteur = Selecteurs.pour(() -> null);
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
}
