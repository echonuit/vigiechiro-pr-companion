package fr.univ_amu.iut.commun.view;

import javafx.beans.property.ReadOnlyStringProperty;

/// Contrat socle **optionnel** d'un écran : fournir un **résumé** à afficher dans la **barre de statut**
/// (pied du chrome) tant que l'écran est au sommet de la navigation.
///
/// Même esprit que [EmplacementNavigation] (fil d'Ariane) : le [Navigateur] lit ce contrat par
/// `instanceof` sur le controller de l'écran courant et **lie** le pied de page à sa propriété ; quand on
/// change d'écran, le pied revient à la mention par défaut. Ainsi une info vivante (par exemple « N
/// observation(s) · X / N revues » de la vue audio) occupe une barre autrement figée, sans que chaque
/// écran ait à gérer son nettoyage.
///
/// Contrat volontairement minimal (un texte). Sa **généralisation en 3 zones** (gauche / centre / droite)
/// est prévue dans la passe d'uniformisation transverse de l'application.
public interface ResumeStatut {

    /// Texte de résumé de l'écran, observé par le chrome et affiché dans la barre de statut. Peut changer
    /// au fil de la vie de l'écran (le pied se met à jour en direct).
    ReadOnlyStringProperty resumeStatutProperty();
}
