package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.LibellesCriteres;
import java.util.ArrayList;
import java.util.List;

/// Retour d'une **opération** d'un écran (import CSV, export, valider, corriger, action refusée…) :
/// un texte + une **sévérité**, exposé par le ViewModel dans une propriété distincte du message
/// d'**état vide** de la table.
///
/// La distinction est délibérée : avant, erreurs d'opération et indice « aucune observation »
/// partageaient la même propriété, donc une erreur d'import s'affichait dans le placeholder gris de la
/// table, indistinguable de « pas de données » (incident « For input string: SUR » invisible). En
/// séparant les deux, la vue rend le retour d'opération dans un **bandeau toujours visible**, coloré
/// selon la sévérité, là où le placeholder gris reste réservé au seul état vide.
///
/// Né dans la vue audio, remonté dans `commun` quand l'Inventaire a eu besoin du même canal (#1837) :
/// « rendre compte sans bloquer » n'a rien de propre à un écran. Se rend avec
/// [fr.univ_amu.iut.commun.view.BandeauRetour].
///
/// La sévérité ne s'écrit **pas** dans le texte : elle se rend une fois, par le composant, en couleur
/// et en icône (#1933). Un pictogramme collé au message le disait une seconde fois, et dépendait des
/// polices de la machine - sur celles qui ne le portent pas, il ne s'affichait pas du tout.
///
/// @param texte message présenté à l'utilisateur (vide = aucun retour à afficher)
/// @param severite gravité du message, portée par [Severite] (socle, #2159)
public record RetourOperation(String texte, Severite severite) {

    /// Glyphes de sévérité, **refusés en tête de message**. Cf. le contrôle du constructeur compact.
    private static final String GLYPHES_DE_SEVERITE = "⚠✓✗❌⛔✅❗";

    /// Refuse un message qui **ouvre par un glyphe de sévérité**.
    ///
    /// La sévérité est portée par [#severite()], et la vue la rend deux fois - en couleur et en icône
    /// ([fr.univ_amu.iut.commun.view.BandeauRetour], [fr.univ_amu.iut.commun.view.LibelleRetour]). Un
    /// « ⚠ » dans la chaîne la dirait une troisième fois, sans que rien ne garantisse l'accord entre les
    /// trois : un message « ⚠ … » posé en `Severite.ERREUR` s'afficherait en rouge avec un cercle barré
    /// et un triangle dans le texte.
    ///
    /// Ce n'est pas une précaution théorique. Avant #2052, faute d'un niveau `AVERTISSEMENT`, huit
    /// propriétés avaient **quitté ce type** pour redevenir des chaînes libres portant leur glyphe
    /// (#2050) - et une fois dehors, plus rien ne bornait leur longueur : trois y joignaient des listes.
    /// La garde ferme la porte par laquelle elles sont sorties.
    ///
    /// Elle ne couvre **que** ce type. Les propriétés `String` ad hoc restent libres d'écrire ce qu'elles
    /// veulent : c'est le trou que #2050 achève de combler, propriété par propriété.
    public RetourOperation {
        if (texte != null && !texte.isEmpty() && GLYPHES_DE_SEVERITE.indexOf(texte.charAt(0)) >= 0) {
            throw new IllegalArgumentException(
                    "La sévérité d'un retour est portée par son niveau, pas par un glyphe en tête de"
                            + " message. Message refusé : " + texte);
        }
    }

    /// Aucun retour à afficher (état nominal).
    public static final RetourOperation AUCUN = new RetourOperation("", Severite.INFO);

    /// Retour de **succès** (vert) : opération réussie, avec un bilan.
    public static RetourOperation succes(String texte) {
        return new RetourOperation(texte, Severite.SUCCES);
    }

    /// Retour d'**information** (neutre) : action refusée ou guidage, sans échec technique.
    public static RetourOperation info(String texte) {
        return new RetourOperation(texte, Severite.INFO);
    }

    /// Retour d'**avertissement** (ambre) : l'opération a abouti, mais quelque chose mérite l'attention.
    ///
    /// Le niveau manquait, et son absence ne s'est pas traduite par des avertissements mal classés : ils
    /// ont **quitté le type** pour redevenir des chaînes libres portant un « ⚠ » en tête (huit propriétés
    /// recensées, #2050). Une fois dehors, plus rien ne bornait leur longueur.
    public static RetourOperation avertissement(String texte) {
        return new RetourOperation(texte, Severite.AVERTISSEMENT);
    }

    /// Retour d'**erreur** (rouge) : l'opération a échoué.
    /// Refus ou échec **remonté d'une exception** : le message y gagne le **geste attendu** quand le
    /// modèle a nommé ce qui manque (#2635). Sans besoin, c'est le message tel quel.
    ///
    /// C'est l'unique endroit où l'IHM ajoute son « comment » : le modèle ne connaît pas les menus, et la
    /// ligne de commande en dit un autre.
    /// Une **vue mémorisée rejouée amputée** de ce qui n'a pas pu être replacé (#3056, puis #3093).
    ///
    /// Le cas se produit quand les libellés offerts ont changé - « Z1 » est devenu « 640380 · Z1 » en
    /// #2995 - ou quand une valeur mémorisée est absente du jeu courant (une espèce qu'on n'a pas
    /// contactée cette fois-ci). La vue s'ouvre quand même, mais elle **filtre moins large** qu'à son
    /// enregistrement : le taire laisserait croire le contraire.
    ///
    /// Avertissement et non erreur : rien n'a échoué, et l'utilisateur n'a rien à réparer.
    public static RetourOperation vueAmputee(String nomVue, ResteDeRestauration reste) {
        return avertissement("La vue « " + nomVue + " » a été rejouée sans " + laisseDeCote(reste)
                + " : elle filtre donc moins large qu'à son enregistrement.");
    }

    /// Des filtres **transportés d'un écran à l'autre** (« Voir sur la carte », #476) que l'écran
    /// d'arrivée n'a pas su reprendre (#3093).
    ///
    /// Ce n'est pas une vue nommée : la phrase ne peut donc pas s'appuyer sur un nom, et doit dire
    /// **d'où** vient l'écart. Le cas est ordinaire et non exceptionnel : Sons & validation offre dix
    /// critères, l'analyse cinq, donc resserrer sur la probabilité puis basculer sur la carte élargit
    /// forcément le résultat.
    /// ⚠️ L'ouverture ne dit **pas** « Cet écran a repris vos filtres » : le fragment des critères
    /// nomme déjà l'écran, et la phrase le répétait deux fois. Vu en régénérant la capture, pas en
    /// relisant le code.
    public static RetourOperation filtresNonRepris(ResteDeRestauration reste) {
        return avertissement("Vos filtres ont été repris sans " + laisseDeCote(reste)
                + " : la liste est donc plus large que celle d'où vous venez.");
    }

    /// Le **choix d'une puce remplacé** parce qu'il a disparu du jeu courant (#3095).
    ///
    /// Quand un autre filtre se resserre, la valeur retenue peut ne plus être offerte. Le critère
    /// retombe alors sur son défaut plutôt que de rester sans choix : l'écran filtre donc sur autre
    /// chose que ce qui avait été demandé, et la table change sans qu'on ait rien touché.
    ///
    /// Le taire serait exactement le défaut que #3056 et #3093 ont corrigé ailleurs.
    public static RetourOperation choixRemplace(String critere, String perdu) {
        return avertissement("« " + perdu + " » n'existe plus dans ce que les autres filtres laissent passer :"
                + " le critère « " + critere + " » est revenu à son choix par défaut.");
    }

    /// Les filtres de la **mémoire de session** (#484) que la réouverture de l'écran n'a pas su
    /// remettre en place (#3093). Les données ont changé entre-temps : c'est le cas le plus courant des
    /// trois, et le plus discret, puisque personne n'a rien demandé.
    public static RetourOperation filtresDeSessionAmputes(ResteDeRestauration reste) {
        return avertissement("Vos filtres précédents ont été remis sans " + laisseDeCote(reste)
                + " : l'écran montre donc plus large que la dernière fois.");
    }

    /// Ce qui n'a pas été replacé, **accordé** et **par cause**. Les deux natures se disent séparément :
    /// une valeur disparue est passagère et tient aux données, un critère absent du catalogue est
    /// structurel et tient à l'écran. Les confondre ferait chercher au mauvais endroit.
    private static String laisseDeCote(ResteDeRestauration reste) {
        List<String> morceaux = new ArrayList<>();
        if (!reste.valeursPerdues().isEmpty()) {
            morceaux.add(manquantes(reste.valeursPerdues()));
        }
        if (!reste.criteresInconnus().isEmpty()) {
            morceaux.add(criteresAbsents(reste.criteresInconnus()));
        }
        return String.join(", ni ", morceaux);
    }

    /// Les valeurs perdues, **accordées**. Au singulier on nomme la valeur plutôt que de la compter :
    /// « sans 1 valeur(s) (Z1) » se lisait mal, et compter jusqu'à un n'apprend rien.
    private static String manquantes(List<String> valeurs) {
        return valeurs.size() == 1
                ? "« " + valeurs.get(0) + " », qui n'existe plus"
                : valeurs.size() + " valeurs qui n'existent plus (" + String.join(", ", valeurs) + ")";
    }

    /// Les critères que cet écran n'offre pas, **accordés**. Nommés par leur **clé** : l'écran ne connaît
    /// pas ces critères, donc n'a pas leur intitulé (cf. [ResteDeRestauration]).
    /// Les critères que cet écran n'offre pas, **nommés comme leur puce les nomme**.
    ///
    /// Deux corrections issues de la revue visuelle du chantier #3092, trouvées en ouvrant la capture :
    ///
    /// - les clés sortaient **telles quelles** (« references », « non_identifie »), tiret bas et
    ///   accents manquants compris, ce qui se lit comme une faute de frappe au milieu d'une phrase
    ///   française. Elles passent par [LibellesCriteres] ;
    /// - « qu'**il** n'offre pas » n'avait d'antécédent que dans un des deux messages. Dans
    ///   [#vueAmputee], le sujet est « La vue », et le pronom ne renvoyait à rien. La formule nomme
    ///   donc l'écran, ce qui reste juste dans les deux.
    private static String criteresAbsents(List<String> criteres) {
        List<String> nommes = criteres.stream().map(LibellesCriteres::de).toList();
        return nommes.size() == 1
                ? "le critère « " + nommes.get(0) + " », que cet écran n'offre pas"
                : nommes.size() + " critères que cet écran n'offre pas (" + String.join(", ", nommes) + ")";
    }

    public static RetourOperation erreur(Throwable refus) {
        return erreur(borner(GesteAttendu.message(refus)));
    }

    /// Échec **situé** : un contexte que nous écrivons, suivi de ce que l'exception rapporte.
    ///
    /// Deux appelants prefixaient le message d'une exception à la main (« Impossible de charger vos
    /// sites : » + `erreur.getMessage()`). Ils perdaient le geste attendu comme les autres, et rien ne
    /// bornait ce qu'ils collaient. Ici le contexte reste entier - c'est notre phrase - et **seule** la
    /// part venue d'ailleurs est bornée.
    public static RetourOperation erreur(String contexte, Throwable refus) {
        return erreur(contexte + borner(GesteAttendu.message(refus)));
    }

    /// Longueur maximale d'un texte venu **d'ailleurs**. Mesurée, pas choisie : à la largeur d'un écran
    /// (1000 px), le bandeau tient 120 caractères par ligne. Deux lignes restent un bandeau ; au-delà, il
    /// pousse le contenu de l'écran vers le bas.
    private static final int LONGUEUR_MAX_EXTERNE = 240;

    /// Borne un message **que nous n'avons pas écrit** (#2076).
    ///
    /// Le bandeau **n'a pas de troncature** : son libellé porte `wrapText`, donc un long message enroule
    /// et fait grandir le bandeau. Mesuré : un message de pilote SQLite rappelant sa requête (379
    /// caractères) le porte à 106 px, un collage de 625 caractères à 186 px - contre 56 px nominal.
    ///
    /// Borné **ici seulement**, et c'est le point : ce qui passe par [#erreur(String)] est écrit par nous,
    /// et sa longueur est notre responsabilité. Ce qui arrive par un [Throwable] vient du pilote SQLite,
    /// d'une réponse HTTP ou d'une trace réseau - personne ne l'a relu.
    ///
    /// Le détail complet n'est pas perdu : le journal le consigne (#1845), et c'est là qu'on va le
    /// chercher pour diagnostiquer. Le bandeau, lui, dit ce qui s'est passé sans déverser.
    private static String borner(String message) {
        if (message == null || message.length() <= LONGUEUR_MAX_EXTERNE) {
            return message;
        }
        return message.substring(0, LONGUEUR_MAX_EXTERNE).stripTrailing() + "… (détail dans le journal)";
    }

    public static RetourOperation erreur(String texte) {
        return new RetourOperation(texte == null ? "Une erreur est survenue." : texte, Severite.ERREUR);
    }

    /// `true` s'il y a un texte à présenter (bandeau visible).
    public boolean present() {
        return texte != null && !texte.isBlank();
    }
}
