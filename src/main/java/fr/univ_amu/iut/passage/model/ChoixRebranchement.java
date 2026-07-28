package fr.univ_amu.iut.passage.model;

import java.nio.file.Path;

/// Comment poser des fichiers retrouvés : les **copier**, ou s'y **référer** là où ils sont (#2255).
///
/// Ce port existe parce que la question ne peut se poser qu'**au milieu** de la réactivation (#2577).
/// Elle n'a d'objet que sur la voie [VoieReactivation#TRANSFORMES] - ailleurs, l'application ne repose
/// pas des fichiers existants, elle les **régénère** dans l'espace de travail, et il n'y a rien à
/// laisser en place. Or la voie ne se connaît qu'après avoir inspecté le dossier **et** hydraté une nuit
/// récupérée : trop tard pour l'assistant, qui a déjà rendu la main.
///
/// Avant, la question était posée systématiquement en amont, et sa réponse **ignorée** sur deux voies
/// sur trois. Demander un choix dont on sait qu'il ne servira pas est pire que ne pas demander : ça
/// coûte une décision à l'utilisateur et ça lui ment sur ce qu'il contrôle.
///
/// **Appelé hors du fil JavaFX**, depuis la procédure. L'implémentation d'IHM revient donc au fil
/// JavaFX pour poser la question et fait **attendre** l'appelant jusqu'à la réponse.
@FunctionalInterface
public interface ChoixRebranchement {

    /// Que faire des fichiers trouvés dans `dossierSource`.
    ///
    /// @param dossierSource le dossier que l'utilisateur a désigné
    /// @param horsEspaceDeTravail vrai si ces fichiers sont **les siens** (hors dossier de travail), ce
    ///     qui rend le référencement recommandable : les recopier ferait un doublon non demandé
    ModeRebranchement choisir(Path dossierSource, boolean horsEspaceDeTravail);

    /// Choix **déjà fait**, pour les surfaces qui n'ont personne à interroger : la ligne de commande, qui
    /// l'exprime par une option, et les appels programmatiques.
    static ChoixRebranchement fixe(ModeRebranchement mode) {
        return (dossierSource, horsEspaceDeTravail) -> mode;
    }

    /// Copier, le comportement historique et le défaut de qui ne demande rien.
    ChoixRebranchement COPIE = fixe(ModeRebranchement.COPIE);
}
