package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet de contraste** (#2115) : les couples texte / fond de la palette respectent le seuil WCAG
/// AA, et la liste de ceux qui ne le respectent pas **ne peut que rétrécir**.
///
/// ## Pourquoi ce test existe
///
/// Rien ne mesurait le contraste dans ce dépôt. Le défaut de #2102 — le gris du texte discret à
/// 4,45:1 pour un seuil de 4,5 — a été trouvé **à la main**, en relevant les pixels d'un aperçu
/// pendant une revue visuelle. Il aurait pu ne jamais l'être, et rien n'empêche un jeton corrigé de
/// redériver ensuite.
///
/// C'est le même raisonnement que [PictogrammesFxmlTest] pose pour les pictogrammes : *le défaut ne
/// se voit ni à la compilation ni à l'exécution sur la machine qui l'écrit, c'est justement pourquoi
/// il a besoin d'un test.*
///
/// ## Un cliquet qui a fini de cliqueter
///
/// Trois jetons échouaient quand ce test a été écrit. Exiger la conformité d'un coup aurait rendu la
/// suite rouge sans que personne n'ait rien cassé, et le test aurait été désactivé avant d'avoir
/// servi : on a donc figé l'état, chaque correction se soldant en retirant sa ligne de
/// [#SOUS_LE_SEUIL_AA].
///
/// **La liste est vide depuis #2115.** Le cliquet est devenu une exigence sèche, ce qui était son but :
/// il n'autorise plus aucune violation, et la mécanique de dette ne reste que pour le jour où une
/// correction demanderait à nouveau plusieurs étapes.
///
/// ## Deux seuils, et pourquoi le second n'est pas une porte de sortie
///
/// WCAG demande 4,5:1 pour du petit texte et 3:1 pour un **élément d'interface**. Un jeton ne bénéficie
/// du second qu'à la condition de **n'habiller aucun texte** — c'est ce qui a fait dédoubler l'ambre en
/// #2115 plutôt que de le déclarer « icône » pour s'épargner l'assombrissement.
///
/// ## Ce qu'il couvre, et ce qu'il ne couvre pas
///
/// Les **jetons** de `palette.css`, sur les fonds où ils sont attestés. **Pas** les 218 couleurs
/// littérales qui vivent hors palette dans les feuilles des features (#1974) : elles définissent des
/// couples que ce test ne voit pas, et c'est la raison pour laquelle le critère de clôture de #2115
/// doit se restreindre aux jetons ou embarquer #1974.
class ContrasteAATest {

    private static final Path PALETTE = Path.of("src/main/java/fr/univ_amu/iut/commun/view/palette.css");

    /// Seuil WCAG AA pour du **petit texte** (moins de 18px, ou 14px gras). Tout le texte secondaire
    /// de l'application est à 12 ou 13px : c'est ce seuil qui s'applique.
    private static final double SEUIL_AA = 4.5;

    /// Seuil WCAG AA pour un **élément d'interface** — icône, trait, bordure porteuse de sens. Il est
    /// plus bas parce qu'une forme se distingue à moins de contraste qu'une lettre.
    ///
    /// Il n'est pas une facilité : un jeton ne s'y range que s'il **n'habille plus aucun texte**. C'est
    /// le cas de `-couleur-avertissement` depuis #2115, où l'ambre a été dédoublé — l'icône garde la
    /// teinte qui la fait repérer, le texte prend `-couleur-avertissement-texte`, plus sombre.
    private static final double SEUIL_AA_ELEMENT = 3.0;

    /// Couples texte / fond **attestés dans les feuilles de style**, pas imaginés. Chaque entrée
    /// renvoie à un usage réel, cité en commentaire.
    private static final Map<String, String> COUPLES = new LinkedHashMap<>();

    static {
        COUPLES.put("-couleur-texte sur -couleur-fond", "-couleur-texte|-couleur-fond");
        COUPLES.put("-couleur-texte-discret sur -couleur-fond", "-couleur-texte-discret|-couleur-fond");
        // `.bandeau-infos` (design.css) : fond propre, très proche du fond général.
        COUPLES.put("-couleur-texte-discret sur bandeau-infos", "-couleur-texte-discret|#f6f8fa");
        COUPLES.put(
                "-couleur-texte-discret sur -couleur-primaire-voile", "-couleur-texte-discret|-couleur-primaire-voile");
        // diagnostic.css:25, sites.css:204/216, design.css:599 — libellés d'état en vert sur fond clair.
        COUPLES.put("-couleur-succes sur -couleur-fond", "-couleur-succes|-couleur-fond");
        // diagnostic.css:31, design.css:960 — « GPS absent », libellé d'avertissement sur fond clair.
        COUPLES.put("-couleur-avertissement-texte sur -couleur-fond", "-couleur-avertissement-texte|-couleur-fond");
        COUPLES.put("-couleur-danger sur -couleur-fond", "-couleur-danger|-couleur-fond");
        COUPLES.put("-couleur-texte-clair sur -couleur-primaire", "-couleur-texte-clair|-couleur-primaire");
        COUPLES.put("-couleur-texte-clair sur -couleur-nuit", "-couleur-texte-clair|-couleur-nuit");
        COUPLES.put("badge succes", "-badge-succes-texte|-badge-succes-fond");
        COUPLES.put("badge avertissement", "-badge-avertissement-texte|-badge-avertissement-fond");
        COUPLES.put("badge danger", "-badge-danger-texte|-badge-danger-fond");
        COUPLES.put("badge neutre", "-badge-neutre-texte|-badge-neutre-fond");
        COUPLES.put("badge info", "-badge-info-texte|-badge-info-fond");
    }

    /// Couples **icône / fond**, vérifiés à [#SEUIL_AA_ELEMENT]. Un jeton n'entre ici que s'il
    /// n'habille plus aucun texte : sinon c'est le seuil du texte qui s'applique, et l'y ranger
    /// reviendrait à se donner raison en changeant la règle.
    private static final Map<String, String> COUPLES_ELEMENT = new LinkedHashMap<>();

    static {
        // diagnostic.css:32, design.css:961 — le triangle d'avertissement, seul usage restant du jeton.
        COUPLES_ELEMENT.put("-couleur-avertissement (icône) sur -couleur-fond", "-couleur-avertissement|-couleur-fond");
    }

    /// **La dette, et elle est soldée.** Quatre couples au moment d'écrire ce test, zéro aujourd'hui :
    /// deux par #2102 (`-couleur-texte-discret`, un seul jeton portait les deux lignes) et deux par
    /// #2115 (le vert et l'ambre). Une liste vide est le seul état où ce test dit ce qu'il promet.
    ///
    /// **Elle doit le rester.** Toute couleur qui repasserait sous son seuil allonge cette liste et
    /// fait échouer ce test : la corriger dans `palette.css` est la réponse, l'inscrire ici n'en est
    /// pas une.
    private static final List<String> SOUS_LE_SEUIL_AA = List.of();

    @Test
    @DisplayName("La dette de contraste ne peut que rétrécir : aucun nouveau couple sous le seuil AA")
    void la_dette_de_contraste_ne_peut_que_retrecir() {
        Map<String, String> jetons = jetonsDeLaPalette();
        List<String> sousLeSeuil = new ArrayList<>();

        for (Map.Entry<String, String> couple : COUPLES.entrySet()) {
            String[] cotes = couple.getValue().split("\\|");
            int[] texte = resoudre(jetons, cotes[0]);
            int[] fond = resoudre(jetons, cotes[1]);
            if (contraste(texte, fond) < SEUIL_AA) {
                sousLeSeuil.add(couple.getKey());
            }
        }

        assertThat(sousLeSeuil).as("""
                        La liste des couples texte/fond sous le seuil WCAG AA a changé.

                        • Elle s'ALLONGE ? Une couleur vient de passer sous 4,5:1. Ce n'est pas un
                          détail esthétique : c'est du texte que certains utilisateurs ne liront pas.
                          Corrigez le jeton dans palette.css plutôt que d'ajouter la ligne ici.

                        • Elle RACCOURCIT ? Bravo : retirez son nom de SOUS_LE_SEUIL_AA, dans ce
                          fichier. C'est le seul geste qui rend le progrès visible.

                        Pourquoi ce cliquet : trois jetons échouaient quand ce test a été écrit (#2115).
                        Exiger la conformité d'un coup aurait rendu la suite rouge sans que personne
                        n'ait rien cassé, et le test aurait été désactivé avant d'avoir servi.

                        Ce test ne voit que les JETONS. Les 218 couleurs littérales des feuilles de
                        features (#1974) définissent des couples qu'il ne mesure pas.
                        """).containsExactlyInAnyOrderElementsOf(SOUS_LE_SEUIL_AA);
    }

    @Test
    @DisplayName("Une couleur qui n'habille que des icônes tient son propre seuil (3:1)")
    void les_elements_d_interface_tiennent_leur_seuil() {
        Map<String, String> jetons = jetonsDeLaPalette();
        List<String> sousLeSeuil = new ArrayList<>();

        for (Map.Entry<String, String> couple : COUPLES_ELEMENT.entrySet()) {
            String[] cotes = couple.getValue().split("\\|");
            if (contraste(resoudre(jetons, cotes[0]), resoudre(jetons, cotes[1])) < SEUIL_AA_ELEMENT) {
                sousLeSeuil.add(couple.getKey());
            }
        }

        assertThat(sousLeSeuil).as("""
                        Une couleur d'élément d'interface est passée sous 3:1.

                        Ces jetons bénéficient d'un seuil plus bas que le texte parce qu'ils
                        n'habillent AUCUN texte : une forme se distingue à moins de contraste qu'une
                        lettre. Le jour où l'un d'eux recolore un libellé, il repasse sous le seuil
                        du texte — déplacez-le dans COUPLES plutôt que de garder l'exemption.

                        C'est le sens du dédoublement de l'ambre (#2115) : l'icône garde la teinte
                        qui la fait repérer, le texte prend un jeton plus sombre.
                        """).isEmpty();
    }

    @Test
    @DisplayName("Le relevé porte sur des jetons réels : la palette est lue, pas devinée")
    void les_jetons_sont_lus_depuis_la_palette() {
        Map<String, String> jetons = jetonsDeLaPalette();

        assertThat(jetons)
                .as("palette.css doit déclarer les jetons que les couples référencent")
                .containsKeys("-couleur-texte", "-couleur-fond", "-couleur-texte-discret");
        for (Map.Entry<String, String> couple : COUPLES.entrySet()) {
            for (String cote : couple.getValue().split("\\|")) {
                assertThat(cote.startsWith("#") || jetons.containsKey(cote))
                        .as("le couple « %s » référence « %s », absent de la palette", couple.getKey(), cote)
                        .isTrue();
            }
        }
    }

    /// Les jetons `-nom: #rrggbb;` déclarés dans `palette.css`.
    private static Map<String, String> jetonsDeLaPalette() {
        Matcher declaration =
                Pattern.compile("(-[a-z-]+)\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;").matcher(lire(PALETTE));
        Map<String, String> jetons = new LinkedHashMap<>();
        while (declaration.find()) {
            jetons.put(declaration.group(1), declaration.group(2));
        }
        return jetons;
    }

    private static int[] resoudre(Map<String, String> jetons, String reference) {
        String hexa = reference.startsWith("#") ? reference : jetons.get(reference);
        return new int[] {
            Integer.parseInt(hexa.substring(1, 3), 16),
            Integer.parseInt(hexa.substring(3, 5), 16),
            Integer.parseInt(hexa.substring(5, 7), 16)
        };
    }

    /// Rapport de contraste WCAG 2.1 entre deux couleurs, `(L1 + 0,05) / (L2 + 0,05)`.
    private static double contraste(int[] a, int[] b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /// Luminance relative WCAG : composantes linéarisées, pondérées 0,2126 / 0,7152 / 0,0722.
    private static double luminance(int[] rgb) {
        double[] lineaire = new double[3];
        for (int i = 0; i < 3; i++) {
            double canal = rgb[i] / 255.0;
            lineaire[i] = canal <= 0.04045 ? canal / 12.92 : Math.pow((canal + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * lineaire[0] + 0.7152 * lineaire[1] + 0.0722 * lineaire[2];
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException("Palette illisible : " + fichier, e);
        }
    }
}
