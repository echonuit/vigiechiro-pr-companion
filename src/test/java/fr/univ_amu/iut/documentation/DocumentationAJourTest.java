package fr.univ_amu.iut.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.cli.commande.CommandeRacine;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/// Garde-fou de **documentation** (#1458) : une commande CLI sans ligne de doc, ou un écran sans fiche,
/// font **rougir la CI**.
///
/// ## Pourquoi ce test existe
///
/// La clôture de l'EPIC #1154 a trouvé deux dérives que **rien** n'aurait signalées :
/// `dev-docs/cli.md` documentait **22 commandes sur 29** (dont quatre livrées par le chantier même), et
/// l'écran « Audit de cohérence » n'avait **aucune fiche** depuis sa livraison (#1133). Une relecture à
/// la main les a vues. C'est précisément ce qu'on ne peut pas garantir.
///
/// Le dépôt défend déjà ses **captures** par quatre garde-fous (`check-doc-images.sh`,
/// `captures.manifest`, `check-captures.sh`, `check-capture-mains.sh`), au nom d'un principe qu'il a
/// tranché : *« une fonctionnalité visible sans capture est une fonctionnalité à moitié livrée »*
/// (`dev-docs/captures.md`). Les **commandes** et les **écrans**, eux, n'avaient rien. Ce test comble
/// l'asymétrie : le même raisonnement vaut pour une commande sans ligne et pour un écran sans page.
///
/// ## Ce qu'il confronte
///
/// Le point de comparaison n'est **jamais une liste tenue à la main** (c'est exactement ce qui dérive),
/// mais la **vérité du câblage** :
///
/// - les sous-commandes déclarées dans l'annotation `@Command` de [CommandeRacine] — lues par
///   **réflexion sur l'annotation**, sans jamais instancier une commande (leurs constructeurs tirent des
///   `Provider` qui ouvrent la base : les instancier ici ferait de l'E/S pour rien) ;
/// - les [ActiviteAccueil] réellement liées dans le `Multibinder` de l'**injecteur**, pas les classes qui
///   ressemblent à des activités.
///
/// Une doc qui ment est **pire** qu'une doc absente : on la croit.
class DocumentationAJourTest {

    /// Surefire s'exécute depuis la racine du projet (`${basedir}`) : les chemins sont relatifs à elle.
    private static final Path DOC_CLI = Path.of("dev-docs", "cli.md");

    private static final Path FICHES = Path.of("docs", "ecrans");

    private static final Path INDEX_FICHES = FICHES.resolve("index.md");

    private static final Path NAV = Path.of("mkdocs.yml");

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Chaque sous-commande câblée dans la CLI figure dans le tableau de dev-docs/cli.md")
    void chaque_commande_cli_est_documentee() {
        String doc = lire(DOC_CLI);

        List<String> absentes = sousCommandesCablees().stream()
                .filter(nom -> !doc.contains("| `" + nom + "`"))
                .sorted()
                .toList();

        assertThat(absentes)
                .as("Ces commandes sont câblées dans CommandeRacine mais ne figurent dans aucune ligne du "
                        + "tableau de dev-docs/cli.md. Une commande qu'on ne documente pas est une commande "
                        + "que personne ne trouvera : ajoutez-lui sa ligne.")
                .isEmpty();
    }

    @Test
    @DisplayName("Chaque activité d'accueil déclare une fiche d'écran qui existe")
    void chaque_activite_a_sa_fiche(@TempDir Path espaceDeTravail) {
        Set<ActiviteAccueil> activites = activitesCablees(espaceDeTravail);

        assertThat(activites)
                .as("aucune activité d'accueil câblée : le test ne prouverait rien")
                .isNotEmpty();

        List<String> sansFiche = activites.stream()
                .filter(activite -> !Files.isRegularFile(FICHES.resolve(activite.pageDoc() + ".md")))
                .map(activite -> activite.titre() + " -> docs/ecrans/" + activite.pageDoc() + ".md")
                .sorted()
                .toList();

        assertThat(sansFiche)
                .as("Ces activités sont offertes sur l'écran d'accueil, mais la fiche qu'elles déclarent "
                        + "n'existe pas. Un écran livré sans page est un écran à moitié livré.")
                .isEmpty();
    }

    @Test
    @DisplayName("Chaque fiche d'écran est atteignable : dans la nav MkDocs et dans l'index de la section")
    void chaque_fiche_est_atteignable() {
        String nav = lire(NAV);
        String index = lire(INDEX_FICHES);
        List<String> fiches = fichesEcrites();

        assertThat(fiches)
                .as("aucune fiche d'écran trouvée : le test ne prouverait rien")
                .isNotEmpty();

        List<String> horsNav = fiches.stream()
                .filter(fiche -> !nav.contains("ecrans/" + fiche + ".md"))
                .toList();
        assertThat(horsNav)
                .as("Ces fiches existent mais n'apparaissent dans aucune entrée `nav` de mkdocs.yml : le site "
                        + "produit ne les publie pas. Une page que le site ne sert pas n'existe pas.")
                .isEmpty();

        List<String> horsIndex = fiches.stream()
                .filter(fiche -> !index.contains("(" + fiche + ".md)"))
                .toList();
        assertThat(horsIndex)
                .as("Ces fiches ne sont liées par aucune ligne du tableau de docs/ecrans/index.md : depuis "
                        + "l'index de leur propre section, l'écran est invisible.")
                .isEmpty();
    }

    /// Noms des sous-commandes tels que picocli les expose à l'utilisateur, lus **sur l'annotation** de
    /// [CommandeRacine] : aucune commande n'est instanciée, donc aucun `Provider` n'ouvre la base.
    private static List<String> sousCommandesCablees() {
        CommandLine.Command racine = CommandeRacine.class.getAnnotation(CommandLine.Command.class);
        return Stream.of(racine.subcommands())
                .map(classe -> classe.getAnnotation(CommandLine.Command.class))
                .filter(Objects::nonNull)
                .map(CommandLine.Command::name)
                .toList();
    }

    /// Les activités **réellement liées** dans le `Multibinder<ActiviteAccueil>` : la vérité du câblage,
    /// pas les classes qui en ont l'air.
    private static Set<ActiviteAccueil> activitesCablees(Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());
        Injector injecteur = RacineInjecteur.creer();
        return injecteur.getInstance(Key.get(new TypeLiteral<Set<ActiviteAccueil>>() {}));
    }

    /// Noms courts des fiches présentes sur le disque, `index.md` exclue (elle est le sommaire, pas un écran).
    private static List<String> fichesEcrites() {
        try (Stream<Path> pages = Files.list(FICHES)) {
            return pages.map(page -> page.getFileName().toString())
                    .filter(nom -> nom.endsWith(".md"))
                    .filter(nom -> !"index.md".equals(nom))
                    .map(nom -> nom.substring(0, nom.length() - ".md".length()))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + FICHES, echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Journal des ADR (#1881)
    // ---------------------------------------------------------------------------------------------

    private static final Path DECISIONS = Path.of("dev-docs", "decisions");

    private static final Path INDEX_ADR = DECISIONS.resolve("index.md");

    /// Navigation du site **développeur**, distincte de celle du site produit ([#NAV]) : c'est elle qui
    /// publie les ADR.
    private static final Path NAV_DEV = Path.of("mkdocs-dev.yml");

    /// Un fichier d'ADR : au moins quatre chiffres, un tiret, un titre en kebab-case.
    ///
    /// **Au moins** quatre, et non exactement : depuis l'[ADR 1881] le numéro est celui de l'issue du
    /// chantier, qui passera un jour à cinq chiffres. Un motif strict laisserait alors ces ADR
    /// **silencieusement hors de portée** de tous les tests ci-dessous, y compris celui des doublons.
    private static final Pattern FICHIER_ADR = Pattern.compile("^(\\d{4,})-[a-z0-9-]+\\.md$");

    /// Dernier numéro attribué par le compteur global, clos par l'[ADR 1881]. Les ADR 0001 à 0048 le
    /// gardent ; au-delà, le numéro est celui de l'issue du chantier.
    private static final int DERNIER_NUMERO_DE_COMPTEUR = 48;

    /// Les deux numéros qu'une résolution de collision a libérés, et qui restent vides : les combler
    /// ferait pointer vers un numéro qui a déjà voulu dire autre chose dans une PR et une discussion.
    private static final Set<Integer> TROUS_DU_COMPTEUR = Set.of(29, 30);

    /// Plancher des numéros d'issue : aucune issue du dépôt n'est descendue sous ce seuil depuis
    /// longtemps, et le compteur, lui, n'ira jamais jusque-là. Il sépare donc sans ambiguïté les deux
    /// régimes de numérotation.
    private static final int PLANCHER_NUMERO_D_ISSUE = 1000;

    /// L'en-tête d'une ADR, qui doit porter le même numéro que son nom de fichier.
    ///
    /// Deux formes coexistent dans le journal (`# ADR 0035 — …` et `# 0026 - …`) et le test **tolère les
    /// deux** : il garde le **numéro**, pas le style de titre. Le rendre strict sur la forme le ferait
    /// rougir sur une variation de rédaction, ce qui est le meilleur moyen de le faire désactiver.
    private static final Pattern ENTETE_ADR = Pattern.compile("^#\\s+(?:ADR\\s+)?(\\d{4,})\\b");

    @Test
    @DisplayName("Deux ADR ne portent jamais le même numéro")
    void les_numeros_d_adr_sont_uniques() {
        Map<String, List<String>> parNumero = new TreeMap<>();
        for (String fichier : fichiersAdr()) {
            Matcher m = FICHIER_ADR.matcher(fichier);
            if (m.matches()) {
                parNumero
                        .computeIfAbsent(m.group(1), numero -> new ArrayList<>())
                        .add(fichier);
            }
        }
        Map<String, List<String>> collisions = new TreeMap<>();
        parNumero.forEach((numero, fichiers) -> {
            if (fichiers.size() > 1) {
                collisions.put(numero, fichiers);
            }
        });

        assertThat(collisions)
                .as("deux chantiers parallèles ont réservé le même numéro d'ADR. C'est arrivé pour de "
                        + "bon (#1881) : la collision ne se découvre alors qu'au conflit de fusion, sur "
                        + "une branche déjà poussée, dans un index que tout le monde édite. Renumérote "
                        + "la plus récente, en balayant TOUTES les branches distantes et pas seulement "
                        + "`main` : une PR ouverte réserve son numéro sans qu'il apparaisse nulle part.")
                .isEmpty();
    }

    @Test
    @DisplayName("Le numéro écrit dans l'en-tête d'une ADR est celui de son nom de fichier")
    void l_entete_d_une_adr_porte_son_numero() {
        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiersAdr()) {
            Matcher nom = FICHIER_ADR.matcher(fichier);
            if (!nom.matches()) {
                continue;
            }
            String premiereLigne =
                    lire(DECISIONS.resolve(fichier)).lines().findFirst().orElse("");
            Matcher entete = ENTETE_ADR.matcher(premiereLigne);
            verifs.assertThat(entete.find() ? entete.group(1) : null)
                    .as(
                            "%s : l'en-tête annonce « %s » au lieu du numéro du fichier. C'est le piège d'une "
                                    + "renumérotation faite à moitié : le fichier change de nom, l'en-tête reste, "
                                    + "et l'ADR se met à porter deux numéros.",
                            fichier, premiereLigne)
                    .isEqualTo(nom.group(1));
        }
        verifs.assertAll();
    }

    @Test
    @DisplayName("#2082 : chaque ADR est atteignable dans la nav du site dev, pas seulement au journal")
    void chaque_adr_est_dans_la_nav() {
        // Le journal (`index.md`) et la nav (`mkdocs-dev.yml`) sont **deux listes** pour une seule
        // vérité, et seule la première était gardée. C'est ainsi que cinq ADR se sont retrouvées
        // publiées mais introuvables autrement qu'en devinant leur URL : leur ligne de journal existait,
        // leur entrée de nav non. MkDocs ne se plaint que de l'inverse (une entrée sans fichier).
        String nav = lire(NAV_DEV);
        List<String> fichiers = fichiersAdr();

        assertThat(fichiers)
                .as("aucune ADR trouvée : le test ne prouverait rien")
                .isNotEmpty();

        List<String> horsNav = fichiers.stream()
                .filter(fichier -> !nav.contains("decisions/" + fichier))
                .toList();

        assertThat(horsNav)
                .as(
                        "Ces ADR existent et sont au journal, mais n'apparaissent dans aucune entrée `nav` de "
                                + "%s : le site dev les publie sans qu'aucun lien n'y mène. Ajoutez-les sous "
                                + "« Décisions », dans l'ordre de leur numéro.",
                        NAV_DEV)
                .isEmpty();
    }

    /// Les trois niveaux de vérification qu'une ADR peut déclarer.
    ///
    /// - `certaine` : l'invariant se prouve. Un test déterministe le garde, et échoue en CI.
    /// - `probable` : l'invariant ne se prouve pas, mais un script liste des **suspects** que l'humain
    ///   trie. Le signal utile est « aucun **nouveau** suspect », d'où le cliquet.
    /// - `humaine` : aucun invariant observable mécaniquement. C'est un classement **légitime**, pas un
    ///   aveu : une décision de méthode ne se vérifie pas par un script, et lui en coller un
    ///   fabriquerait un contrôle creux, c'est-à-dire pire que rien.
    private static final Set<String> NIVEAUX_DE_VERIFICATION = Set.of("certaine", "probable", "humaine");

    /// La puce d'en-tête qui déclare le niveau, à côté de `Statut` et `Chantier`.
    private static final Pattern VERIFICATION_ADR =
            Pattern.compile("^- \\*\\*Vérification\\*\\* : (\\w+) — (.+)$", Pattern.MULTILINE);

    /// Une vérification `certaine` nomme son test : `ClasseDeTest#nom_de_la_methode`.
    private static final Pattern REFERENCE_TEST = Pattern.compile("^`(\\w+)#([a-z0-9_]+)`$");

    /// L'en-tête d'une ADR, ramené à la forme composée.
    ///
    /// Le motif contient un `é` : si le fichier porte sa forme **décomposée** (`e` suivi de l'accent
    /// combinant, ce que produisent certains éditeurs et le système de fichiers macOS), la comparaison
    /// échoue alors que le texte est visuellement identique. Le garde-fou annoncerait une ADR non
    /// déclarée sur un fichier parfaitement conforme.
    private static String lireNormalise(Path fichier) {
        return Normalizer.normalize(lire(fichier), Normalizer.Form.NFC);
    }

    /// Une vérification `probable` nomme son script et le cliquet en vigueur.
    private static final Pattern REFERENCE_SCRIPT = Pattern.compile("^`([^`]+)` \\(cliquet : (\\d+)\\)$");

    /// Une vérification `certaine` peut aussi nommer un script, sans cliquet : il n'a rien à tolérer.
    private static final Pattern REFERENCE_SCRIPT_SEUL = Pattern.compile("^`([^`]+)`$");

    /// La partie « Loupe : `chemin` » qu'une vérification `humaine` peut adjoindre à son motif.
    private static final Pattern LOUPE_HUMAINE = Pattern.compile("Loupe : `([^`]+)`");

    /// Les ADR pas encore classées, dispensées de déclarer **le temps de l'être**.
    ///
    /// Classer 47 décisions est un travail de jugement, pas une formalité : le faire d'un bloc pour
    /// rendre ce test vert produirait 47 classements bâclés, dont une majorité de `humaine` posés sans
    /// examen. La liste rend donc la dette **visible et décroissante**, là où un test désactivé se
    /// serait fait oublier.
    private static final Path NON_DECLAREES = Path.of("scripts", "adr", "non-declarees.txt");

    /// Noms de fichiers listés dans [NON_DECLAREES], commentaires et lignes vides écartés.
    private static List<String> adrNonDeclarees() {
        return lire(NON_DECLAREES)
                .lines()
                .map(String::strip)
                .filter(ligne -> !ligne.isEmpty() && !ligne.startsWith("#"))
                .toList();
    }

    @Test
    @DisplayName("Chaque ADR déclare, dans son en-tête, comment elle est vérifiée")
    void chaque_adr_declare_comment_elle_est_verifiee() {
        List<String> aClasser = adrNonDeclarees();
        SoftAssertions verifs = new SoftAssertions();

        // Le cliquet ne protège que dans un sens : il dispense de déclarer, jamais de retirer son nom
        // une fois la déclaration écrite. Sans cette moitié-là, la liste se figerait pleine et
        // dispenserait éternellement - un tapis, pas un cliquet.
        List<String> aRetirer = aClasser.stream()
                .filter(fichier -> Files.exists(DECISIONS.resolve(fichier)))
                .filter(fichier -> VERIFICATION_ADR
                        .matcher(lireNormalise(DECISIONS.resolve(fichier)))
                        .find())
                .toList();
        verifs.assertThat(aRetirer)
                .as(
                        "Ces ADR déclarent désormais leur vérification mais figurent encore dans %s : "
                                + "retirez-les de la liste, sinon elle cesse de décroître.",
                        NON_DECLAREES)
                .isEmpty();

        List<String> fantomes = aClasser.stream()
                .filter(fichier -> !Files.exists(DECISIONS.resolve(fichier)))
                .toList();
        verifs.assertThat(fantomes)
                .as(
                        "Ces noms figurent dans %s mais aucune ADR ne porte ce nom : la liste dispense des "
                                + "fichiers qui n'existent plus.",
                        NON_DECLAREES)
                .isEmpty();

        // Le backlog de classement est CLOS : les 49 ADR déclarent leur vérification. La liste ne doit
        // plus jamais se re-remplir. Une ADR nouvelle déclare son niveau dans la PR qui l'introduit, au
        // même titre qu'elle porte un Statut et un Chantier - elle ne passe pas par une dette différée.
        verifs.assertThat(aClasser)
                .as(
                        "%s doit rester vide : le classement des ADR est terminé. Une ADR nouvelle déclare "
                                + "sa vérification tout de suite, elle ne s'ajoute pas ici pour plus tard.",
                        NON_DECLAREES)
                .isEmpty();

        for (String fichier : fichiersAdr()) {
            Matcher declaration = VERIFICATION_ADR.matcher(lireNormalise(DECISIONS.resolve(fichier)));
            if (!declaration.find()) {
                if (aClasser.contains(fichier)) {
                    continue; // Classement à venir, tracé dans la liste décroissante.
                }
                verifs.fail(
                        "%s : aucune puce « **Vérification** » dans l'en-tête. Une ADR dont personne ne sait "
                                + "si elle est tenue finit par n'être tenue par personne. Déclarez son niveau "
                                + "(%s) à côté de **Statut** et **Chantier**.",
                        fichier, NIVEAUX_DE_VERIFICATION);
                continue;
            }
            verifs.assertThat(declaration.group(1))
                    .as(
                            "%s : niveau de vérification « %s » inconnu. Les seuls admis sont %s.",
                            fichier, declaration.group(1), NIVEAUX_DE_VERIFICATION)
                    .isIn(NIVEAUX_DE_VERIFICATION);
        }
        verifs.assertAll();
    }

    @Test
    @DisplayName("Le test ou le script qu'une ADR déclare existe vraiment")
    void la_verification_declaree_par_une_adr_existe_vraiment() {
        // Le cœur du dispositif. Déclarer un contrôle ne coûte rien ; c'est de le vérifier que vient la
        // protection. Sans ce test, un script supprimé ou un test renommé laisserait l'ADR annoncer une
        // garde qui n'existe plus - exactement la dérive qui a fait pointer trois renvois du cycle de
        // chantier vers un dépôt vidé de ses sources.
        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiersAdr()) {
            Matcher declaration = VERIFICATION_ADR.matcher(lireNormalise(DECISIONS.resolve(fichier)));
            if (!declaration.find()) {
                continue; // Absence déjà signalée par le test précédent.
            }
            String reference = declaration.group(2).trim();
            switch (declaration.group(1)) {
                case "certaine" -> verifierReferenceCertaine(verifs, fichier, reference);
                case "probable" -> verifierScriptNomme(verifs, fichier, reference);
                case "humaine" -> verifierMotifHumain(verifs, fichier, reference);
                default -> {
                    // Niveau inconnu : déjà signalé par le test précédent.
                }
            }
        }
        verifs.assertAll();
    }

    /// Une vérification `humaine` porte un motif, et peut adjoindre une **loupe**.
    ///
    /// La loupe (`scripts/adr/loupe-XXXX-*.py`) ne prouve ni ne borne rien : elle surface une surface de
    /// revue pour la passe humaine, et ne bloque jamais la CI. Mais si l'ADR en annonce une, elle doit
    /// exister - même exigence que pour un test ou un script, faute de quoi l'ADR renverrait vers un
    /// outil disparu.
    private static void verifierMotifHumain(SoftAssertions verifs, String fichier, String reference) {
        Matcher loupe = LOUPE_HUMAINE.matcher(reference);
        String motif = loupe.find() ? reference.substring(0, loupe.start()).trim() : reference;

        verifs.assertThat(motif.length())
                .as(
                        "%s : une vérification « humaine » doit dire POURQUOI aucun contrôle mécanique n'est "
                                + "possible. « %s » est trop court pour être un motif.",
                        fichier, motif)
                .isGreaterThanOrEqualTo(20);

        if (loupe.reset().find()) {
            verifs.assertThat(Path.of(loupe.group(1)))
                    .as(
                            "%s : l'ADR annonce la loupe `%s`, qui n'existe pas. Une loupe absente est une "
                                    + "aide à la revue qu'on croit avoir et qui n'aidera personne.",
                            fichier, loupe.group(1))
                    .exists();
        }
    }

    /// Une vérification `certaine` nomme un test **ou** un script.
    ///
    /// Les deux sont déterministes, et l'ADR 0040 le prouve : le sujet de commit est gardé par
    /// `verifie-titre-pr.sh`, aussi sûrement qu'un test, mais ce n'est pas du JUnit. Exiger une classe
    /// aurait forcé à reclasser en `probable` une décision parfaitement tenue - le format aurait menti
    /// sur la solidité du contrôle.
    private static void verifierReferenceCertaine(SoftAssertions verifs, String fichier, String reference) {
        Matcher nomme = REFERENCE_TEST.matcher(reference);
        // La forme « test » se reconnaît EN PREMIER : les deux références sont encadrées d'accents
        // graves, et le motif « script » accepte donc aussi `Classe#methode`. Tester le plus général
        // d'abord envoyait toute référence de test se faire chercher comme un fichier - le garde-fou
        // s'est pris lui-même à ce piège en s'exécutant.
        if (!nomme.matches() && REFERENCE_SCRIPT_SEUL.matcher(reference).matches()) {
            verifierScriptExiste(verifs, fichier, reference.replace("`", ""));
            return;
        }
        if (!nomme.matches()) {
            verifs.fail(
                    "%s : une vérification « certaine » nomme son test (`ClasseDeTest#nom_du_test`) ou "
                            + "son script (`chemin/du/script`). Reçu : %s",
                    fichier, reference);
            return;
        }
        String classe = nomme.group(1);
        String methode = nomme.group(2);
        Optional<Path> source = chercherSource(classe + ".java");
        if (source.isEmpty()) {
            verifs.fail(
                    "%s : l'ADR annonce le test `%s#%s`, mais aucune classe %s.java n'existe. L'ADR se "
                            + "croit gardée et ne l'est pas.",
                    fichier, classe, methode, classe);
            return;
        }
        verifs.assertThat(lire(source.get()))
                .as(
                        "%s : la classe %s existe mais ne contient aucune méthode `%s`. Un test renommé "
                                + "laisse l'ADR annoncer une garde disparue.",
                        fichier, classe, methode)
                .contains(methode);
    }

    private static void verifierScriptNomme(SoftAssertions verifs, String fichier, String reference) {
        Matcher nomme = REFERENCE_SCRIPT.matcher(reference);
        if (!nomme.matches()) {
            verifs.fail(
                    "%s : une vérification « probable » nomme son script et son cliquet, sous la forme "
                            + "`chemin/du/script` (cliquet : N). Reçu : %s",
                    fichier, reference);
            return;
        }
        verifierScriptExiste(verifs, fichier, nomme.group(1));
    }

    private static void verifierScriptExiste(SoftAssertions verifs, String fichier, String chemin) {
        verifs.assertThat(Path.of(chemin))
                .as(
                        "%s : l'ADR annonce le script `%s`, qui n'existe pas. Un rapport qu'aucun script "
                                + "n'alimente est un silence qu'on prend pour un feu vert.",
                        fichier, chemin)
                .exists();
    }

    private static Optional<Path> chercherSource(String nomDeFichier) {
        try (Stream<Path> sources = Files.walk(Path.of("src", "test", "java"))) {
            return sources.filter(chemin -> chemin.getFileName().toString().equals(nomDeFichier))
                    .findFirst();
        } catch (IOException echec) {
            throw new UncheckedIOException("parcours de src/test/java", echec);
        }
    }

    @Test
    @DisplayName("Chaque ADR est au journal, et chaque ligne du journal pointe vers une ADR existante")
    void le_journal_des_adr_et_les_fichiers_se_correspondent() {
        String journal = lire(INDEX_ADR);
        List<String> fichiers = fichiersAdr();

        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiers) {
            verifs.assertThat(journal)
                    .as(
                            "%s n'a pas de ligne dans le journal (`%s`) : une ADR absente de l'index est une "
                                    + "décision qu'on ne retrouvera pas.",
                            fichier, INDEX_ADR)
                    .contains(fichier);
        }

        Matcher lien = Pattern.compile("\\((\\d{4,}-[a-z0-9-]+\\.md)\\)").matcher(journal);
        while (lien.find()) {
            verifs.assertThat(fichiers)
                    .as(
                            "le journal renvoie vers « %s », qui n'existe pas. Une renumérotation a laissé "
                                    + "sa ligne derrière elle.",
                            lien.group(1))
                    .contains(lien.group(1));
        }
        verifs.assertAll();
    }

    @Test
    @DisplayName("#1881 : le compteur d'ADR reste clos, personne ne reprend « le premier numéro libre »")
    void le_compteur_d_adr_reste_clos() {
        Set<Integer> attendus = new TreeSet<>();
        for (int numero = 1; numero <= DERNIER_NUMERO_DE_COMPTEUR; numero++) {
            if (!TROUS_DU_COMPTEUR.contains(numero)) {
                attendus.add(numero);
            }
        }

        Set<Integer> presents = new TreeSet<>();
        for (String fichier : fichiersAdr()) {
            Matcher nom = FICHIER_ADR.matcher(fichier);
            if (nom.matches() && Integer.parseInt(nom.group(1)) < PLANCHER_NUMERO_D_ISSUE) {
                presents.add(Integer.parseInt(nom.group(1)));
            }
        }

        assertThat(presents)
                .as("la série numérotée au compteur a bougé. Elle est close depuis l'ADR 1881 : une ADR"
                        + " neuve prend le numéro de l'issue de son chantier, pas « le premier numéro"
                        + " libre ». Un numéro en trop, c'est le compteur qui rouvre, et avec lui les"
                        + " collisions entre chantiers parallèles ; un numéro en moins, c'est une ADR"
                        + " effacée ou un des deux trous (0029, 0030) comblé, alors qu'ils désignent"
                        + " déjà autre chose dans une PR et une discussion.")
                .isEqualTo(attendus);
    }

    @Test
    @DisplayName("#1881 : une ADR postérieure à la bascule porte le numéro de son chantier")
    void une_adr_recente_porte_le_numero_de_son_chantier() {
        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiersAdr()) {
            Matcher nom = FICHIER_ADR.matcher(fichier);
            if (!nom.matches()) {
                continue;
            }
            int numero = Integer.parseInt(nom.group(1));
            if (numero < PLANCHER_NUMERO_D_ISSUE) {
                continue;
            }
            String chantier = lire(DECISIONS.resolve(fichier))
                    .lines()
                    .filter(ligne -> ligne.startsWith("- **Chantier**"))
                    .findFirst()
                    .orElse("");
            verifs.assertThat(chantier)
                    .as(
                            "%s : sa ligne « Chantier » ne cite pas #%d. Depuis l'ADR 1881, le numéro d'une"
                                    + " ADR n'est pas un rang mais une référence : il doit désigner l'issue qui"
                                    + " porte la décision, sans quoi il ne renvoie à aucune discussion.",
                            fichier, numero)
                    .contains("#" + numero);
        }
        verifs.assertAll();
    }

    /// Les fichiers d'ADR présents sur le disque, `index.md` exclue (c'est le journal, pas une décision).
    private static List<String> fichiersAdr() {
        try (Stream<Path> pages = Files.list(DECISIONS)) {
            return pages.map(page -> page.getFileName().toString())
                    .filter(nom -> nom.endsWith(".md"))
                    .filter(nom -> !"index.md".equals(nom))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + DECISIONS, echec);
        }
    }
}
