package fr.univ_amu.iut.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde-fous des décisions **vérifiables de façon certaine**.
///
/// ## Pourquoi cette classe existe
///
/// Une ADR énonce une règle, puis vit sa vie. Rien ne dit, six mois plus tard, si le code la respecte
/// encore : il faudrait relire, et on ne relit pas 49 décisions à chaque chantier. Les ADR déclarent
/// donc désormais **comment** elles sont vérifiées, et celles dont l'invariant se prouve pointent vers
/// un test d'ici.
///
/// ## Ce qui n'y entre pas, et pourquoi
///
/// Toutes les décisions ne peuvent pas y figurer. « Le plan précède l'écriture » ou « la mesure fait
/// foi en CI » sont des décisions de méthode : aucun scan ne dira si elles sont tenues. Leur coller un
/// contrôle fabriquerait un test creux, c'est-à-dire pire que rien - le vert qu'il afficherait ne
/// mesurerait aucun fait.
///
/// Deux conditions pour entrer ici : l'invariant se formule comme un motif observable, et il **tient
/// déjà** sur le dépôt. Un invariant violé le jour de sa naissance n'est pas une garde, c'est une dette
/// déguisée en test rouge : il part en `probable`, avec un script de suspects et un cliquet
/// (`scripts/adr/`).
///
/// L'ADR 0035 a fait le trajet inverse pendant l'écriture de cette classe : classée « certaine » sur
/// une mesure qui annonçait zéro infraction, elle s'est révélée en compter - la mesure scannait
/// `src/main/resources`, où il n'y a aucun FXML. D'où la garde de non-vacuité que porte chaque test qui
/// balaie des fichiers : **un test qui ne trouve rien à examiner doit échouer**, pas passer.
class DecisionsRespecteesTest {

    /// Surefire s'exécute depuis la racine du projet : les chemins sont relatifs à elle.
    private static final Path POM = Path.of("pom.xml");

    @Test
    @DisplayName("ADR 0038 : l'échelle de sévérité compte quatre niveaux, et son ordre porte la sémantique")
    void l_echelle_de_severite_a_quatre_niveaux_dans_l_ordre() {
        // L'ordre de DÉCLARATION est la sémantique : il gouverne les comparaisons et le classement des
        // constats. Ajouter un niveau au milieu, ou en déplacer un, change silencieusement le sens de
        // tout code qui compare deux sévérités.
        assertThat(Severite.values())
                .as("L'ADR 0038 fixe quatre niveaux, dans cet ordre précis : AVERTISSEMENT s'insère entre "
                        + "INFO et ERREUR parce que l'opération a abouti mais mérite l'attention. Toute "
                        + "insertion ou permutation déplace le sens des comparaisons existantes.")
                .containsExactly(Severite.SUCCES, Severite.INFO, Severite.AVERTISSEMENT, Severite.ERREUR);
    }

    @Test
    @DisplayName("ADR 0045 : l'installeur Windows porte ses constantes d'identité")
    void l_installeur_windows_porte_ses_constantes_d_identite() {
        // Sans --win-upgrade-uuid, jpackage tire un UUID ALÉATOIRE à chaque build (JDK-8214564) : chaque
        // version devient un produit distinct, rien ne désinstalle l'ancienne, et `winget upgrade`
        // installe à côté au lieu de remplacer. La panne serait invisible ici, et visible chez
        // l'utilisateur des mois plus tard.
        String pom = lire(POM);

        assertThat(pom)
                .as("Le profil jpackage-windows doit figer l'UpgradeCode : sans lui, jpackage en tire un "
                        + "au hasard à chaque build, et les montées de version cessent de remplacer "
                        + "l'installation précédente.")
                .contains("--win-upgrade-uuid")
                .contains("0328d083-bdf7-4e84-95bf-918249478c00");

        assertThat(pom)
                .as("Le scope de l'installeur est l'autre constante d'identité : passer de per-user à "
                        + "per-machine ferait cohabiter deux installations chez l'utilisateur.")
                .contains("--win-per-user-install");
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }
}
