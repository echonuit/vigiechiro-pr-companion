package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel.Envoi;

/// Ce qu'un « Appliquer » de la modale de rattachement a **réellement fait**, dit d'un seul tenant.
///
/// Extrait de [RattachementViewModel] : la composition d'un compte rendu est une fonction **pure** de
/// ses deux moitiés, elle n'a que faire de l'état d'un ViewModel. L'y laisser l'avait d'ailleurs fait
/// basculer en God Class au portail PMD, ce qui était le bon signal.
public final class CompteRenduRattachement {

    private CompteRenduRattachement() {}

    /// Le compte rendu d'un « Appliquer » : **ce qui a été fait** localement, puis ce que l'envoi a donné
    /// (#3449).
    ///
    /// « Modifier le passage » fait deux choses, renommer les séquences sur le disque puis pousser vers la
    /// plateforme, et la modale n'annonçait que la seconde : un renommage intégralement réussi se soldait
    /// par un bandeau rouge « Envoi impossible », d'où l'utilisateur conclut que rien ne s'est passé.
    ///
    /// **L'ordre** : ce qui a été fait se dit avant ce qui n'a pas pu l'être, c'est la partie irréversible.
    /// **Le registre** : un succès partiel n'est ni une erreur ni un succès ordinaire, donc la modale
    /// retient ([Envoi.ALire]) le temps qu'il soit lu, comme pour le réalignement des heures (#1885). Sans
    /// renommage, le compte rendu de l'envoi **est** le compte rendu, et il passe intact.
    public static Envoi de(int sequencesRenommees, Envoi envoi) {
        if (sequencesRenommees <= 0) {
            return envoi;
        }
        String renommage = sequencesRenommees + " séquence(s) renommée(s).";
        if (envoi instanceof Envoi.SansObjet) {
            return new Envoi.ALire(renommage);
        }
        return new Envoi.ALire(renommage + " " + envoi.retour().texte());
    }
}
