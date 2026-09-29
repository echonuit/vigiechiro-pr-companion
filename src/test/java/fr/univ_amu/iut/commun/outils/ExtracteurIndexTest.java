package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.CtModel;

/// L'extracteur écrit des signatures qui PORTENT LEURS PARAMÈTRES, et ne retient qu'un appel venu
/// d'ailleurs.
///
/// Ce cas existe parce que rien ne le tenait, et c'est la passe 6 de la clôture de #5464 qui l'a
/// constaté. L'extracteur est livré depuis #5473 ; ses deux lecteurs Python s'éprouvent sur un index
/// **fabriqué**, délibérément, pour ne pas mesurer le corpus du jour ; et le pas de CI de #5531 refuse
/// seulement si l'extraction échoue. Une clé qui perdrait ses paramètres fusionnerait donc les **436**
/// surcharges du corpus sans que rien ne rougisse.
///
/// Le modèle est bâti sur deux sources jetables plutôt que sur le dépôt : `modele()` balaie les deux
/// racines de paquet et coûte 25 s, ce qui n'a pas sa place dans la suite.
class ExtracteurIndexTest {

    /// Le niveau que l'extracteur pose lui-même : Spoon le déduirait du JDK courant et refuserait
    /// `-25` avec « Unrecognized option ».
    private static final int COMPLIANCE = 21;

    @Test
    void la_signature_porte_ses_parametres(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index =
                indexDe(ou, "Seule", "package p; public class Seule { public void f(int n) {} public void g() {} }");

        assertThat(index.keySet()).contains("p.Seule#f(int)", "p.Seule#g()");
    }

    @Test
    void deux_surcharges_ne_partagent_pas_une_entree(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index = indexDe(
                ou, "Deux", "package p; public class Deux { public void f(int n) {} public void f(String s) {} }");

        assertThat(index.keySet()).contains("p.Deux#f(int)", "p.Deux#f(String)");
    }

    @Test
    void un_appel_depuis_le_meme_type_ne_compte_pas(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index =
                indexDe(ou, "Dedans", "package p; public class Dedans { void aide() {} void geste() { aide(); } }");

        assertThat(index.get("p.Dedans#aide()")).isEmpty();
    }

    @Test
    void un_appel_depuis_un_autre_type_compte_et_le_nomme(@TempDir Path ou) throws IOException {
        ecrire(ou, "Cible", "package p; public class Cible { public void aide() {} }");
        Map<String, List<String>> index =
                indexDe(ou, "Appelante", "package p; public class Appelante { void geste() { new Cible().aide(); } }");

        assertThat(index.get("p.Cible#aide()")).containsExactly("p.Appelante");
    }

    /// Le contraste des deux cas précédents tient à un détail que le nom de la méthode ne dit pas :
    /// `appelantsHorsDuFichier` compare des **types**, pas des fichiers. Une classe imbriquée dans le
    /// même fichier compte donc comme un appelant venu d'ailleurs, et c'est ce cas qui le fixe plutôt
    /// qu'un lecteur ne le redécouvre.
    @Test
    void hors_du_fichier_se_mesure_par_type_donc_une_classe_imbriquee_compte(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index = indexDe(
                ou,
                "Englobante",
                "package p; public class Englobante {"
                        + " void aide() {}"
                        + " static class Dedans { void geste() { new Englobante().aide(); } } }");

        assertThat(index.get("p.Englobante#aide()")).containsExactly("p.Englobante$Dedans");
    }

    /// La méthode d'un type IMBRIQUÉ est une clé, et c'est le défaut que #5564 a corrigé.
    ///
    /// `getAllTypes()` ne rend que le premier niveau, si bien que 1 226 méthodes de types imbriqués
    /// nommés n'étaient pas des clés - alors que 154 de ces types figuraient comme APPELANTS. Aucun
    /// cas ne surveillait cette moitié, et la limite déclarée de la classe affirmait le contraire.
    @Test
    void la_methode_d_un_type_imbrique_est_une_cle(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index = indexDe(
                ou, "Porteuse", "package p; public class Porteuse {" + " static class Dedans { void aide() {} } }");

        assertThat(index.keySet()).contains("p.Porteuse$Dedans#aide()");
    }

    @Test
    void un_contrat_du_modele_rend_les_types_qui_le_tiennent(@TempDir Path ou) throws IOException {
        ecrire(ou, "Contrat", "package p; public interface Contrat { void tenir(); }");
        ecrire(ou, "Une", "package p; public class Une implements Contrat { public void tenir() {} }");
        Map<String, List<String>> index =
                contratsDe(ou, "Autre", "package p; public class Autre implements Contrat { public void tenir() {} }");

        assertThat(index.get("p.Contrat")).containsExactly("p.Autre", "p.Une");
    }

    /// Vingt-deux des 117 interfaces du dépôt sont imbriquées - `EcritureAtomique.Attente`,
    /// `TransportVigieChiro.CorpsAEnvoyer` et les autres. Les écarter faisait répondre « personne ne
    /// l'implémente » à une question dont la réponse existe.
    @Test
    void une_interface_IMBRIQUEE_est_un_contrat_comme_une_autre(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index = contratsDe(
                ou,
                "Englobe",
                "package p; public class Englobe {"
                        + " interface Dedans { void tenir(); }"
                        + " static class Tient implements Dedans { public void tenir() {} } }");

        assertThat(index.get("p.Englobe$Dedans")).containsExactly("p.Englobe$Tient");
    }

    @Test
    void une_classe_abstraite_est_un_contrat_et_sa_fille_le_tient(@TempDir Path ou) throws IOException {
        ecrire(ou, "Socle", "package p; public abstract class Socle { abstract void tenir(); }");
        Map<String, List<String>> index =
                contratsDe(ou, "Fille", "package p; public class Fille extends Socle { void tenir() {} }");

        assertThat(index.get("p.Socle")).containsExactly("p.Fille");
    }

    /// Un contrat HORS du modèle n'est pas une clé, et c'est le filtre qui rend l'index lisible : sans
    /// lui, chaque `Comparable` ou `Runnable` du JDK entrerait avec ses porteurs.
    @Test
    void un_contrat_hors_du_modele_n_est_pas_une_cle(@TempDir Path ou) throws IOException {
        Map<String, List<String>> index = contratsDe(
                ou,
                "Comparable",
                "package p; public class Sujet implements java.lang.Runnable { public void run() {} }");

        assertThat(index.keySet()).noneMatch(c -> c.contains("Runnable"));
    }

    private static Map<String, List<String>> contratsDe(Path ou, String nom, String source) throws IOException {
        ecrire(ou, nom, source);
        return ExtracteurIndex.implementationsParContrat(modeleDe(ou));
    }

    private static Map<String, List<String>> indexDe(Path ou, String nom, String source) throws IOException {
        ecrire(ou, nom, source);
        return ExtracteurIndex.appelantsHorsDuFichier(modeleDe(ou));
    }

    private static void ecrire(Path ou, String nom, String source) throws IOException {
        Files.writeString(ou.resolve(nom + ".java"), source);
    }

    private static CtModel modeleDe(Path ou) {
        Launcher lanceur = new Launcher();
        lanceur.getEnvironment().setComplianceLevel(COMPLIANCE);
        lanceur.getEnvironment().setNoClasspath(true);
        lanceur.getEnvironment().setCommentEnabled(false);
        lanceur.addInputResource(ou.toString());
        lanceur.buildModel();
        return lanceur.getModel();
    }
}
