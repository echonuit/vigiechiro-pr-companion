package fr.univ_amu.iut.lot.viewmodel;

/// État d'une archive de dépôt dans la table de suivi (#820), qui pilote son rendu (couleur + icône +
/// libellé) et sa barre de progression : gris « en attente » → bleu « en cours » → vert « terminée »,
/// ou rouge « échec ».
public enum EtatArchive {

    /// Planifiée mais pas encore compressée (gris) : le plan est connu, l'écriture n'a pas commencé.
    EN_ATTENTE("En attente"),

    /// En cours de compression (bleu) : la barre de la ligne progresse de 0 à 1.
    EN_COURS("En cours"),

    /// Compressée avec succès (vert) : taille réelle connue.
    TERMINEE("Terminée"),

    /// Compression échouée (rouge).
    ECHEC("Échec");

    private final String libelle;

    EtatArchive(String libelle) {
        this.libelle = libelle;
    }

    /// Libellé lisible de l'état (affichage + accessibilité).
    public String libelle() {
        return libelle;
    }
}
