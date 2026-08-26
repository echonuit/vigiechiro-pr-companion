package fr.univ_amu.iut.commun.model;

import java.util.IdentityHashMap;
import java.util.Set;

/// Ce qu'une alerte d'incident **montre** d'une exception (#3470).
///
/// Une exception arrive au filet global **emballée** : quand FXML invoque un `onAction="#methode"` par
/// réflexion, ce qui est levé dedans part en `InvocationTargetException`, relancée en
/// `RuntimeException(cette enveloppe)` - et `RuntimeException(Throwable)` pose comme message
/// `cause.toString()`. Afficher ce message-là nomme le **transport**, jamais la panne : une chaîne
/// exacte, sans valeur, et que rien ne fait rougir puisqu'elle n'est pas vide.
///
/// ## La règle : le message le plus profond QUI PARLE
///
/// Ce n'est **pas** « prendre la cause racine », et la nuance décide du résultat : la plus profonde
/// peut être un `NullPointerException` muet, moins parlant que son parent. Dérouler jusqu'au bout
/// rendrait l'alerte **plus pauvre** qu'avant, en ayant l'air de la corriger.
///
/// On descend donc la chaîne et l'on retient le **dernier** message informatif rencontré : les
/// enveloppes n'en portent pas, la panne réelle en porte un, et ce qui vient après elle n'en porte
/// souvent plus.
///
/// ## Pourquoi une classe, et pas trois lignes dans `App`
///
/// Parce que ce filet précis a déjà bouclé **seize mille fois** en production avant qu'on le voie
/// (#3700), et qu'une lambda posée dans `start` ne s'éprouve pas. Même raison, même endroit.
///
/// C'est aussi ce qui impose les deux garde-fous ci-dessous : composer ce message est le geste que le
/// filet fait **au pire moment**, quand quelque chose vient déjà de casser.
public final class CauseLisible {

    /// Le libellé de l'entrée de menu qui ouvre le dossier des journaux.
    ///
    /// **Il vit ICI, et la vue le cite** - l'inverse de ce que #3470 avait posé. La raison est
    /// arrivée avec #3947 : ce message est composé par le filet global des **deux** surfaces, et une
    /// surface headless qui va chercher une constante dans un paquet `view` lit mal. Ce qui faisait
    /// tenir le montage précédent n'était pas une propriété d'architecture, c'était l'**inlining** par
    /// javac d'une constante de compilation - retirer le `final` l'aurait cassé sans rien faire rougir.
    ///
    /// La citation reste un lien de compilation, donc l'**ADR 3854** tient par construction : renommer
    /// l'entrée de menu renomme ce que le message dit d'aller chercher. La première rédaction disait
    /// « Journaux », entrée qui n'existe pas.
    ///
    /// **Et « menu principal », pas le pictogramme ☰.** Une rédaction l'employait ; le garde
    /// `PoliceCouvreLIhmTest` l'a refusée, à juste titre : ce caractère n'est pas dans la Noto Sans
    /// embarquée, il partirait en repli vers une police du système et deux utilisateurs ne verraient
    /// pas le même glyphe (ADR 0035).
    public static final String LIBELLE_ENTREE_JOURNAUX = "Ouvrir le dossier des journaux";

    /// Où regarder **à l'écran** : l'entrée de menu, nommée telle qu'elle s'écrit.
    public static final String OU_REGARDER_IHM = "menu principal > " + LIBELLE_ENTREE_JOURNAUX;

    /// Où regarder **en ligne de commande**. Elle n'a pas de menu, et lui en désigner un serait une
    /// consigne inapplicable : le seul repli honnête est le chemin du dossier.
    ///
    /// C'est le piège qu'un alignement naïf aurait posé. Faire appeler `messageDe` par la CLI sans
    /// toucher à ce repli aurait mis « menu principal » dans une sortie de terminal, et le défaut
    /// n'aurait rien fait rougir : le message serait resté non vide, donc d'apparence correcte. C'est
    /// exactement la forme du défaut que l'ADR 3470 combat, déplacée d'un cran.
    public static final String OU_REGARDER_CLI = "le dossier logs/ de votre espace de travail";

    /// Ce que le message dit quand la chaîne entière est muette : le type, et où regarder.
    ///
    /// Il affichait « null » dans ce cas - `String.valueOf` d'un message nul. Un mot qui ne veut rien
    /// dire est pire qu'un type : il ne se cherche même pas.
    private static final String SANS_MESSAGE =
            "%s, sans message. Le détail et la trace complète sont dans le journal de l'application (%s).";

    /// Ce que le message dit quand il n'y a rien du tout à décrire.
    private static final String SANS_EXCEPTION =
            "Incident sans détail disponible. Le journal de l'application (%s) en garde la trace.";

    private CauseLisible() {
        // Porte une règle, pas un état.
    }

    /// Le message à montrer pour `erreur` : le plus profond de sa chaîne qui dise quelque chose, ou un
    /// repli qui nomme le type et le journal.
    ///
    /// @param erreur l'exception attrapée par le filet, éventuellement `null` - le filet compose ce
    ///     message alors que quelque chose vient de casser, il ne doit pas casser à son tour
    public static String messageDe(Throwable erreur) {
        return messageDe(erreur, OU_REGARDER_IHM);
    }

    /// Le même message, pour une surface qui ne désigne pas le journal de la même façon.
    ///
    /// @param erreur l'exception attrapée par le filet, éventuellement `null`
    /// @param ouRegarder où cette surface dit d'aller chercher la trace ([#OU_REGARDER_IHM],
    ///     [#OU_REGARDER_CLI])
    public static String messageDe(Throwable erreur, String ouRegarder) {
        if (erreur == null) {
            return SANS_EXCEPTION.formatted(ouRegarder);
        }

        // Identité et non égalité : deux exceptions distinctes peuvent être « égales » au sens d'equals
        // sans être le même maillon, et c'est le maillon qu'on suit.
        Set<Throwable> vus = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        String retenu = null;

        // Une chaîne de causes peut se REFERMER sur elle-même : `initCause` le permet, et un
        // déroulement naïf y tourne sans fin. Le filet global est le dernier endroit où l'on peut se
        // le permettre : c'est déjà lui qui a bouclé en #3700.
        for (Throwable maillon = erreur; maillon != null && vus.add(maillon); maillon = maillon.getCause()) {
            String message = maillon.getMessage();
            if (message != null && !message.isBlank() && !estLeNomDeSaCause(maillon, message)) {
                retenu = message.strip();
            }
        }

        return retenu != null ? retenu : SANS_MESSAGE.formatted(nomLisible(erreur), ouRegarder);
    }

    /// `true` si `message` n'est que le `toString()` de la cause de `maillon` - c'est-à-dire le message
    /// que la JDK **fabrique toute seule**, et non un message écrit par quelqu'un.
    ///
    /// **C'est le cœur du défaut, et il se généralise.** `RuntimeException(Throwable)` - comme tous
    /// les constructeurs `(Throwable)` de la JDK - pose comme message `cause.toString()`. Le
    /// « `java.lang.reflect.InvocationTargetException` » du retour de terrain n'était pas un cas
    /// particulier de la réflexion : c'était cette convention-là, et elle produit le même texte inutile
    /// derrière n'importe quelle enveloppe.
    ///
    /// Ce garde-fou a été trouvé par un **rouge inattendu** : sans lui, une chaîne entièrement muette
    /// rendait « java.lang.IllegalStateException », que la règle « non vide, donc informatif » retenait
    /// comme un message. On aurait remplacé un nom de classe par un autre.
    private static boolean estLeNomDeSaCause(Throwable maillon, String message) {
        Throwable cause = maillon.getCause();
        return cause != null && message.equals(cause.toString());
    }

    /// Le nom court du type, sans son paquetage : `IllegalStateException` plutôt que
    /// `java.lang.IllegalStateException`. Le paquetage n'apprend rien à qui lit l'alerte, et le nom
    /// court reste ce qu'on recherchera dans le journal.
    private static String nomLisible(Throwable erreur) {
        Throwable plusProfonde = erreur;
        Set<Throwable> vus = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        while (vus.add(plusProfonde) && plusProfonde.getCause() != null && vus.add(plusProfonde.getCause())) {
            plusProfonde = plusProfonde.getCause();
        }
        return plusProfonde.getClass().getSimpleName();
    }
}
