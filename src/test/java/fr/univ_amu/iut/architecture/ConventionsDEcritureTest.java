package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde : les **deux conventions d'écriture** que `CONTRIBUTING.md` énonce et que rien ne tenait
/// (#2946).
///
/// ## Pourquoi elles sont ici ensemble
///
/// Ce sont deux puces voisines de la même liste, toutes deux vérifiables sur le **texte** des sources,
/// et toutes deux dans le même état au moment d'écrire ce fichier : respectées, et gardées par rien.
///
/// ## Ce que le chantier #2365 a démontré à leur sujet
///
/// La règle du tiret cadratin était écrite deux fois, appliquée par personne, et elle a dérivé pendant
/// des années. Elle avait fini par être enfreinte **dans le fichier qui l'énonce**, et deux fois de plus
/// dans les commentaires qui expliquaient pourquoi il fallait la tenir. Une convention que seule la
/// relecture applique n'est pas une convention, c'est un souhait.
///
/// Ces deux règles-ci étaient à zéro violation, ce qui ne prouve rien : la règle typographique l'était
/// aussi, à l'échelle d'un fichier, avant de ne plus l'être à l'échelle du dépôt. Un zéro non gardé ne
/// reste pas zéro.
///
/// ## Pourquoi un test et non un cliquet
///
/// L'[ADR 2843](../../../../../../../dev-docs/decisions/2843-typographie-cliquet-plutot-que-nettoyage.md)
/// pose le cliquet pour une dette qu'on résorbe **par tranches**. Ces deux zones sont déjà au plancher :
/// une marge n'y servirait qu'à laisser passer la première rechute. Un refus, donc, et pas un compteur.
class ConventionsDEcritureTest {

    private static final List<Path> ARBRES = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    @Test
    @DisplayName("#2946 : les doc-comments sont en Markdown `///`, jamais en `/** */` HTML")
    void doc_comments_en_markdown() throws IOException {
        List<String> fautifs = new ArrayList<>();
        for (Path source : sources()) {
            List<String> lignes = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lignes.size(); i++) {
                // Le motif se cherche en DEBUT de ligne. Un `/**` au milieu d'une ligne est un glob de
                // chemin (`commun/**`) ou un morceau de texte, jamais une ouverture de doc-comment :
                // l'audit qui a motivé ce garde avait justement remonté un `commun/**` cité dans un
                // commentaire `///`, et l'aurait compté comme une violation.
                if (lignes.get(i).stripLeading().startsWith("/**")) {
                    fautifs.add(source + ":" + (i + 1));
                }
            }
        }
        assertThat(fautifs).as("""
                        Ces doc-comments sont écrits en `/** */` HTML. La convention du dépôt est le \
                        doc-comment Markdown `///` (JEP 467), qui se lit tel quel dans l'éditeur et ne \
                        demande ni balise ni astérisque de continuation.

                        Remède : remplacer le bloc par des lignes `///`.""").isEmpty();
    }

    @Test
    @DisplayName("#2946 : aucun identifiant ne porte d'accent")
    void identifiants_sans_accent() throws IOException {
        List<String> fautifs = new ArrayList<>();
        for (Path source : sources()) {
            String code = codeSeul(Files.readString(source, StandardCharsets.UTF_8));
            for (int i = 0; i < code.length(); i++) {
                char c = code.charAt(i);
                if (Character.isLetter(c) && c > 127) {
                    fautifs.add(source + " : « " + c + " » dans " + extrait(code, i));
                    break; // un signalement par fichier suffit à le faire ouvrir
                }
            }
        }
        assertThat(fautifs).as("""
                        Ces identifiants portent un accent. Les noms de classes et de membres sont en \
                        français, mais leurs identifiants restent ASCII : un accesseur `détail()` oblige \
                        chaque appelant à composer le caractère, et se cherche mal.

                        Le dernier cas connu était le composant de record `ResultatLancement.détail`, \
                        renommé en `detail` avec ses cinq appels (#2946).""").isEmpty();
    }

    /// Un Stage **reçu en paramètre** : celui de `start(Stage …)` que TestFX fournit, ou celui qu'un
    /// utilitaire de test se voit confier.
    private static final Pattern STAGE_RECU = Pattern.compile("Stage\\s+(\\w+)\\s*[,)]");

    /// Une affectation d'un Stage reçu à un champ : `fenetre = stage;`, `this.fenetre = stage;`.
    private static final String AFFECTATION = "(?:this\\.)?(\\w+)\\s*=\\s*%s\\s*;";

    @Test
    @DisplayName("#4134 : aucune classe de test ne fige un Stage qu'elle a reçu")
    void aucun_stage_recu_n_est_fige() throws IOException {
        List<String> fautifs = new ArrayList<>();
        for (Path source : sources()) {
            if (!source.startsWith(Path.of("src/test/java"))) {
                continue;
            }
            String code = codeSeul(Files.readString(source, StandardCharsets.UTF_8));
            for (String nom : nomsDesStagesRecus(code)) {
                Matcher fige = Pattern.compile("\\b" + Pattern.quote(nom) + "\\.(setWidth|setHeight)\\s*\\(")
                        .matcher(code);
                while (fige.find()) {
                    fautifs.add("%s:%d : %s.%s(…)".formatted(source, ligne(code, fige.start()), nom, fige.group(1)));
                }
            }
        }
        assertThat(fautifs).as("""
                        Ces appels figent un Stage que la classe n'a pas créé.

                        `setWidth` / `setHeight` font passer un Stage en dimensionnement EXPLICITE : il \
                        cesse DEFINITIVEMENT de s'ajuster aux scènes qu'on lui pose ensuite. Sans \
                        conséquence sur une fenêtre qu'on jette, mais le Stage du harnais TestFX est \
                        partagé par toutes les classes d'un même fork : figé par l'une, il fait échouer \
                        les suivantes sur des noeuds « invisibles », très loin de la cause et seulement \
                        selon l'ordre d'exécution. C'est ce que le job `ordre-alternatif` attrape.

                        Reposer la valeur en sortie ne suffit pas, et c'est le piège de ce défaut : la \
                        largeur revient, le dimensionnement explicite reste. Mesuré - une fenêtre figée \
                        à 900 ignore la scène suivante ; après `sizeToScene()` elle vaut 33, puis 369 \
                        avec la scène d'après.

                        Remède : demander la taille à la MISE EN PAGE (taille préférée sur la racine) \
                        puis `sizeToScene()`, comme `recette.FenetreDuBanc`. Pour une fenêtre à soi, \
                        `new Stage()` - et alors elle n'est plus reçue, et ce garde ne la regarde plus.

                        Ce défaut en est à sa quatrième venue : #1940, #1967, #3452, #4130.""").isEmpty();
    }

    @Test
    @DisplayName("#4229 : un dialogue du produit n'emprunte pas ses boutons à la locale du poste")
    void un_dialogue_n_emprunte_pas_ses_boutons_a_la_locale() throws IOException {
        List<String> fautifs = new ArrayList<>();
        for (Path source : sources()) {
            if (!source.startsWith(Path.of("src/main/java"))) {
                continue;
            }
            String code = codeSeul(Files.readString(source, StandardCharsets.UTF_8));
            Matcher standard = Pattern.compile("\\bButtonType\\.(OK|CANCEL|CLOSE|YES|NO)\\b")
                    .matcher(code);
            while (standard.find()) {
                fautifs.add(
                        "%s:%d : ButtonType.%s".formatted(source, ligne(code, standard.start()), standard.group(1)));
            }
        }

        assertThat(fautifs).as("""
                        Ces boutons portent un libellé que JAVAFX traduit depuis la locale de la \
                        MACHINE, pas depuis l'application.

                        Tout le reste de cette interface est en français écrit en dur : le produit \
                        serait donc français partout, sauf sur les boutons de ses dialogues, qui \
                        suivraient le poste. Un utilisateur français sur un système anglais lit un \
                        message français et clique « Cancel ».

                        Remède : `BoutonsDeDialogue.CONFIRMER`, `ANNULER`, `FERMER`, qui portent le \
                        libellé français ET conservent le `ButtonData` - c'est lui qui dit à JavaFX \
                        lequel est le bouton par défaut et lequel ferme sur Échap.""").isEmpty();
    }

    /// Les noms sous lesquels un fichier de test désigne un Stage qu'il a **reçu** : les paramètres de
    /// type `Stage`, et les champs auxquels ils sont affectés.
    ///
    /// Les champs comptent, et c'est par eux que le défaut est passé deux fois : `fenetre = stage`
    /// dans `start`, puis `fenetre.setWidth(...)` cinquante lignes plus bas. Ne regarder que le
    /// paramètre laisserait les deux classes fautives vertes.
    private static Set<String> nomsDesStagesRecus(String code) {
        Set<String> noms = new LinkedHashSet<>();
        Matcher recus = STAGE_RECU.matcher(code);
        while (recus.find()) {
            noms.add(recus.group(1));
        }
        for (String recu : Set.copyOf(noms)) {
            Matcher affecte =
                    Pattern.compile(AFFECTATION.formatted(Pattern.quote(recu))).matcher(code);
            while (affecte.find()) {
                noms.add(affecte.group(1));
            }
        }
        return noms;
    }

    /// Le numéro de ligne d'une position dans le code dépouillé. Le dépouillement remplace chaque
    /// caractère retiré par une espace : les positions, donc les lignes, restent celles du fichier.
    private static int ligne(String code, int position) {
        return (int) code.substring(0, position).chars().filter(c -> c == '\n').count() + 1;
    }

    /// Les sources Java des deux arbres.
    private static List<Path> sources() throws IOException {
        List<Path> trouvees = new ArrayList<>();
        for (Path arbre : ARBRES) {
            if (!Files.isDirectory(arbre)) {
                continue;
            }
            try (Stream<Path> chemins = Files.walk(arbre)) {
                chemins.filter(p -> p.toString().endsWith(".java")).forEach(trouvees::add);
            }
        }
        assertThat(trouvees)
                .as("Le garde ne balaie aucun fichier : lancé hors de la racine du dépôt ?")
                .isNotEmpty();
        return trouvees;
    }

    /// Le code **débarrassé** de ses commentaires et de ses littéraux, chaque caractère retiré étant
    /// remplacé par une espace pour préserver les positions.
    ///
    /// C'est ce dépouillement qui rend le second garde fiable, et il a été écrit après un faux positif
    /// instructif. Chercher une **déclaration** par motif (`String\s+(\w+)`) remontait `double préfixe`,
    /// pris dans le commentaire « jamais de double préfixe » : le mot-clé de type y était suivi d'un mot
    /// accentué, et rien ne distinguait la ligne d'une vraie déclaration. Sur du code ainsi dépouillé,
    /// en revanche, toute lettre non ASCII **est** un identifiant, sans motif à ajuster.
    private static String codeSeul(String contenu) {
        StringBuilder net = new StringBuilder(contenu.length());
        int i = 0;
        while (i < contenu.length()) {
            char c = contenu.charAt(i);
            if (c == '/' && i + 1 < contenu.length() && contenu.charAt(i + 1) == '/') {
                i = blanchirJusqua(contenu, net, i, "\n", false);
            } else if (c == '/' && i + 1 < contenu.length() && contenu.charAt(i + 1) == '*') {
                i = blanchirJusqua(contenu, net, i, "*/", true);
            } else if (c == '"' || c == '\'') {
                i = blanchirLitteral(contenu, net, i, c);
            } else {
                net.append(c);
                i++;
            }
        }
        return net.toString();
    }

    /// Blanchit de `debut` jusqu'à `fin` incluse (ou jusqu'au bout), en gardant les sauts de ligne pour
    /// que les positions restent lisibles.
    private static int blanchirJusqua(String contenu, StringBuilder net, int debut, String fin, boolean inclure) {
        int stop = contenu.indexOf(fin, debut + 2);
        int borne = stop < 0 ? contenu.length() : (inclure ? stop + fin.length() : stop);
        for (int j = debut; j < borne; j++) {
            net.append(contenu.charAt(j) == '\n' ? '\n' : ' ');
        }
        return borne;
    }

    /// Blanchit un littéral de chaîne ou de caractère, en respectant les échappements. Les blocs de
    /// texte `"""` sont couverts sans cas particulier : leurs guillemets internes sont eux-mêmes des
    /// ouvertures et fermetures, et le contenu finit blanchi de proche en proche.
    private static int blanchirLitteral(String contenu, StringBuilder net, int debut, char borne) {
        net.append(' ');
        int j = debut + 1;
        while (j < contenu.length()) {
            char c = contenu.charAt(j);
            if (c == '\\') {
                net.append("  ");
                j += 2;
                continue;
            }
            net.append(c == '\n' ? '\n' : ' ');
            j++;
            if (c == borne) {
                break;
            }
        }
        return j;
    }

    /// Une trentaine de caractères autour de la position fautive, pour que le message situe le défaut
    /// sans qu'on ait à ouvrir le fichier.
    private static String extrait(String code, int position) {
        int debut = Math.max(0, position - 15);
        int fin = Math.min(code.length(), position + 15);
        return "« " + code.substring(debut, fin).replace('\n', ' ').strip() + " »";
    }
}
