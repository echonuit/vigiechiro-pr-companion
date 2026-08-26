package fr.univ_amu.iut.fixture;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/// **Ce qu'une commande a écrit** (#2866) : les deux flux, capturés en mémoire, relus en UTF-8.
///
/// ## Pourquoi cette classe existe
///
/// Vingt-quatre fichiers de test montaient le même échafaudage : deux `ByteArrayOutputStream`, deux
/// `PrintStream` en UTF-8, et un accesseur maison pour relire le tampon. Rien là-dedans n'est propre au
/// test qui le monte.
///
/// L'encodage est la raison d'être de ce regroupement. Un `PrintStream` construit sans charset explicite
/// prend celui de la plateforme : le test passe sur une machine et échoue ailleurs, sur un `é` ou une
/// apostrophe typographique. Le fixer à un seul endroit, c'est le fixer partout.
///
/// ## Usage
///
/// ```java
/// private final SortieCapturee capture = new SortieCapturee();
///
/// int code = cli.executer(new String[] {"solde-saison"}, capture.sortie(), capture.erreur());
/// assertThat(capture.texte()).contains("Solde de la saison");
/// ```
///
/// Une même instance peut servir plusieurs invocations : [#vider()] remet les deux tampons à zéro entre
/// deux appels.
public final class SortieCapturee {

    private final ByteArrayOutputStream tamponSortie = new ByteArrayOutputStream();
    private final ByteArrayOutputStream tamponErreur = new ByteArrayOutputStream();
    private final PrintStream sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
    private final PrintStream erreur = new PrintStream(tamponErreur, true, StandardCharsets.UTF_8);

    /// Le flux à passer en sortie standard.
    public PrintStream sortie() {
        return sortie;
    }

    /// Le flux à passer en sortie d'erreur.
    public PrintStream erreur() {
        return erreur;
    }

    /// Ce qui a été écrit sur la sortie standard depuis le dernier [#vider()].
    public String texte() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    /// Ce qui a été écrit sur la sortie d'erreur depuis le dernier [#vider()].
    public String texteErreur() {
        return tamponErreur.toString(StandardCharsets.UTF_8);
    }

    /// Les deux flux **mis bout à bout**, la sortie standard d'abord.
    ///
    /// Pour les tests qui ne se soucient pas de savoir par où un message est sorti : ils passaient
    /// autrefois **un seul tampon** aux deux flux, ce qui les mêlait dans l'ordre chronologique.
    ///
    /// La différence est réelle et se voit sur un seul cas : l'**entrelacement**. Un tampon unique rend
    /// « A(std) B(err) C(std) » ; cette méthode rend « A C B ». Un `contains` sur un fragment n'y voit
    /// rien ; une comparaison au texte entier, si. Les tests migrés relèvent tous du premier cas, sauf
    /// l'approbation de recette - dont le golden, inchangé, prouve que rien n'est parti sur l'erreur.
    public String tout() {
        return texte() + texteErreur();
    }

    /// Remet **les deux** tampons à zéro, pour enchaîner une seconde invocation sur la même instance.
    ///
    /// Les deux, et pas seulement la sortie standard : un test qui vidait `tamponSortie` seul et
    /// affirmait ensuite quelque chose sur l'erreur *cumulée* des deux invocations changerait de sens. Ce
    /// cas n'existe pas dans les fichiers migrés, il a été cherché fichier par fichier.
    public void vider() {
        tamponSortie.reset();
        tamponErreur.reset();
    }
}
