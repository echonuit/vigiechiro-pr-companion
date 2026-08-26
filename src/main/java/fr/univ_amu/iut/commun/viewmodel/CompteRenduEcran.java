package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/// Le **compte rendu d'un écran** : ce que son bandeau de retour affiche, et la commande qui l'efface.
///
/// Un ViewModel qui porte un bandeau en détient un et l'expose par un seul accesseur, au lieu de
/// recopier le trio propriété / poser / effacer. Ce trio se répète aujourd'hui à l'identique dans
/// plusieurs ViewModels : il est ici nommé une fois, et le rapprochement des autres relève d'une
/// harmonisation à part, pas de cette classe.
///
/// Il est né d'une contrainte de taille, et c'est en soi une information : ajouter le trio à
/// [fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel] lui faisait franchir le seuil God-class
/// (#4091), exactement ce qui en avait déjà sorti `PublicationDepuisLaFiche`. Un ViewModel de fiche
/// détaillée est un point d'accumulation : chaque concept qu'on peut en nommer séparément le rend
/// lisible, et le seuil sert d'alarme plutôt que de règle arbitraire.
///
/// Ce qu'il faut y voir : la sévérité et le texte vivent dans [RetourOperation], la couleur et l'icône
/// dans `BandeauRetour`. Cette classe ne décide de rien, elle **retient**.
public final class CompteRenduEcran {

    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Ce que le bandeau observe, [RetourOperation#AUCUN] tant qu'il n'y a rien à dire.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Porte un compte rendu au bandeau.
    public void rendre(RetourOperation compteRendu) {
        retour.set(Objects.requireNonNull(compteRendu, "compteRendu"));
    }

    /// Efface le compte rendu : l'utilisateur a lu le bandeau et en ferme la croix.
    public void effacer() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Efface le compte rendu **s'il porte une erreur**, et lui seul.
    ///
    /// Un rechargement réussi dément l'échec qui annonçait la lecture impossible : le garder afficherait
    /// le contraire de ce que l'écran montre. Mais il n'a rien à annuler de ce qu'une **autre** opération
    /// a dit, et c'est la nuance qui manquait (#4099) : le corollaire de l'ADR 0023 vise ce qui **démarre**
    /// une opération, pas ce qui relit la donnée.
    ///
    /// Mesuré sur le scénario de recette S1-37 : récupérer un carré écrit le carré **et** ses points,
    /// donc autant d'`insert` que de rechargements déclenchés par la révision - cinq. Le compte rendu du
    /// geste était posé puis effacé cinq fois, avant d'avoir pu être lu, et rien ne le signalait.
    public void oublierUnEchec() {
        if (retour.get().severite() == Severite.ERREUR) {
            retour.set(RetourOperation.AUCUN);
        }
    }
}
