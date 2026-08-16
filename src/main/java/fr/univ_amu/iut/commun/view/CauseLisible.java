package fr.univ_amu.iut.commun.view;

import java.util.IdentityHashMap;
import java.util.Set;

/// Ce qu'une alerte d'incident **montre** d'une exception (#3470).
///
/// ## Le défaut : un message exact et inutile
///
/// Un utilisateur a vu, en désignant la racine de sa carte SD :
///
/// > **Une erreur inattendue est survenue**
/// > `java.lang.reflect.InvocationTargetException`
///
/// La chaîne n'était pas absente, elle était **exacte et sans valeur** : elle nommait le mécanisme de
/// transport, jamais la panne. C'est pourquoi rien ne rougissait - un texte non vide a l'air d'un
/// message.
///
/// D'où elle venait : quand FXML invoque un `onAction="#methode"` par réflexion, ce qui est levé dedans
/// part en `InvocationTargetException`, puis est relancé en `RuntimeException(cette enveloppe)`. Or
/// `RuntimeException(Throwable)` pose comme message `cause.toString()`. Le filet global affichait ce
/// message-là.
///
/// ## La règle : le message le plus profond QUI PARLE
///
/// ⚠️ Ce n'est **pas** « prendre la cause racine », et la nuance décide du résultat : la plus profonde
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

    /// Où l'utilisateur doit aller chercher la trace.
    ///
    /// ⚠️ Le geste est **cité** depuis [ActionOuvrirJournaux], pas recopié : un message qui renvoie
    /// vers une entrée de menu doit la nommer telle qu'elle s'écrit, et le rester si elle est renommée
    /// (ADR 3854). La première rédaction disait « Journaux », entrée qui n'existe pas.
    ///
    /// ⚠️ **Et « menu principal », pas le pictogramme ☰.** La deuxième rédaction l'employait ; le garde
    /// `PoliceCouvreLIhmTest` l'a refusée, à juste titre : ce caractère n'est pas dans la Noto Sans
    /// embarquée, il partirait donc en repli vers une police du système et deux utilisateurs ne
    /// verraient pas le même glyphe (ADR 0035). La forme retenue est celle que le reste de
    /// l'application emploie déjà - « menu principal > Entrée ».
    private static final String OU_REGARDER = "menu principal > " + ActionOuvrirJournaux.LIBELLE;

    /// Ce que l'alerte dit quand la chaîne entière est muette : le type, et où regarder.
    ///
    /// Elle affichait « null » dans ce cas - `String.valueOf` d'un message nul. Un mot qui ne veut rien
    /// dire est pire qu'un type : il ne se cherche même pas.
    private static final String SANS_MESSAGE =
            "%s, sans message. Le détail et la trace complète sont dans le journal de l'application (" + OU_REGARDER
                    + ").";

    /// Ce que l'alerte dit quand il n'y a rien du tout à décrire.
    private static final String SANS_EXCEPTION =
            "Incident sans détail disponible. Le journal de l'application (" + OU_REGARDER + ") en garde la trace.";

    private CauseLisible() {
        // Porte une règle, pas un état.
    }

    /// Le message à montrer pour `erreur` : le plus profond de sa chaîne qui dise quelque chose, ou un
    /// repli qui nomme le type et le journal.
    ///
    /// @param erreur l'exception attrapée par le filet, éventuellement `null` - le filet compose ce
    ///     message alors que quelque chose vient de casser, il ne doit pas casser à son tour
    public static String messageDe(Throwable erreur) {
        if (erreur == null) {
            return SANS_EXCEPTION;
        }

        // Identité et non égalité : deux exceptions distinctes peuvent être « égales » au sens d'equals
        // sans être le même maillon, et c'est le maillon qu'on suit.
        Set<Throwable> vus = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        String retenu = null;

        // ⚠️ Une chaîne de causes peut se REFERMER sur elle-même : `initCause` le permet, et un
        // déroulement naïf y tourne sans fin. Le filet global est le dernier endroit où l'on peut se
        // le permettre : c'est déjà lui qui a bouclé en #3700.
        for (Throwable maillon = erreur; maillon != null && vus.add(maillon); maillon = maillon.getCause()) {
            String message = maillon.getMessage();
            if (message != null && !message.isBlank() && !estLeNomDeSaCause(maillon, message)) {
                retenu = message.strip();
            }
        }

        return retenu != null ? retenu : SANS_MESSAGE.formatted(nomLisible(erreur));
    }

    /// `true` si `message` n'est que le `toString()` de la cause de `maillon` - c'est-à-dire le message
    /// que la JDK **fabrique toute seule**, et non un message écrit par quelqu'un.
    ///
    /// ⚠️ **C'est le cœur du défaut, et il se généralise.** `RuntimeException(Throwable)` - comme tous
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
