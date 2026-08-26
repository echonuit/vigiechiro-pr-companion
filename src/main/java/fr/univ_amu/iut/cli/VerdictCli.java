package fr.univ_amu.iut.cli;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.CauseLisible;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.RefusAvantEcriture;

/// Ce qu'une exception vaut pour celui qui a lancé la commande : une **nature**, une **phrase**, et le
/// **code de sortie** qui en découle (#3570).
///
/// ## Pourquoi une pièce à part
///
/// Ce mappage vivait dans le gestionnaire d'erreurs de picocli, donc il exigeait une `CommandLine`. Or
/// `Cli.main` fait deux choses **avant** que picocli n'existe : il amorce la journalisation et migre la
/// base. Leurs exceptions n'avaient alors **aucun** gestionnaire : la JVM imprimait
/// `Exception in thread "main"` et la pile, et le processus sortait en `1` - y compris pour un
/// `RefusAvantEcriture`, que #3498 avait pourtant appris à traduire en `2`.
///
/// Le séparer donne **un seul endroit qui décide**, et deux qui impriment.
///
/// @param nature ce qui est arrivé, du point de vue de l'appelant
/// @param phrase ce qu'on lui écrit, préfixe compris
record VerdictCli(Nature nature, String phrase) {

    /// Ce que l'appelant doit comprendre, et qui commande à la fois le code et le journal.
    enum Nature {

        /// Invocation invalide détectée dans la logique (un point introuvable) : ni incident ni refus
        /// métier. Ne mérite pas de trace : c'est une faute de frappe, pas un événement.
        USAGE,

        /// Refus **avant écriture** : règle métier, validateur, ou garde de la persistance. L'état local
        /// est **intact**, et c'est exactement ce que le code 2 promet (convention #2294).
        REFUS,

        /// Panne inattendue, état **incertain**. Sa trace est l'information utile - dans les journaux,
        /// pas sur la sortie d'erreur d'un script.
        INCIDENT
    }

    /// `RefusAvantEcriture` rejoint les refus (#3146), alors qu'il **hérite** de
    /// `DataAccessException` : il est émis avant d'avoir écrit quoi que ce soit. L'ordre des tests
    /// compte donc, et c'est le genre de détail qu'un remaniement casse sans rien faire rougir - d'où
    /// le test qui le nomme.
    static VerdictCli de(Exception echec) {
        if (echec instanceof ErreurUsage) {
            return new VerdictCli(Nature.USAGE, "Erreur d'usage : " + echec.getMessage());
        }
        if (echec instanceof RegleMetierException
                || echec instanceof IllegalArgumentException
                || echec instanceof RefusAvantEcriture) {
            // Le message du modèle dit ce qui MANQUE ; c'est ici qu'on ajoute quoi taper (#2635).
            return new VerdictCli(Nature.REFUS, "Refus : " + GesteAttenduCli.message(echec));
        }
        return new VerdictCli(
                Nature.INCIDENT, "Échec : " + CauseLisible.messageDe(echec, CauseLisible.OU_REGARDER_CLI));
    }

    int code() {
        return switch (nature) {
            case USAGE -> Cli.CODE_ERREUR_ARGUMENTS;
            case REFUS -> Cli.CODE_REFUS;
            case INCIDENT -> Cli.CODE_ERREUR_EXECUTION;
        };
    }
}
