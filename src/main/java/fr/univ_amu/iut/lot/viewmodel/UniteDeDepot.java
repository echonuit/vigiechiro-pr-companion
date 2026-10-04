package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/// Le **nom** de ce qu'un dépôt envoie, pour que l'écran en rende compte dans les mots de ce qui est
/// parti (#5824).
///
/// Le compte rendu écrivait « archive(s) » partout, alors qu'un dépôt en séquences WAV, le défaut depuis
/// #5677, n'en envoie aucune. La table de suivi nommait déjà chaque ligne d'après son type ; le compte
/// rendu suit la même règle, à l'échelle du plan.
///
/// Les trois noms sont féminins : les phrases s'accordent sans se dédoubler.
public enum UniteDeDepot {

    /// Le plan ne porte que des archives ZIP.
    ARCHIVE("archive", "archives"),

    /// Le plan ne porte que des séquences WAV.
    SEQUENCE("séquence", "séquences"),

    /// Le plan mêle les deux, ce qu'un dépôt forcé par `--wav` sur des archives déjà en ligne produit.
    UNITE("unité", "unités");

    private final String singulier;
    private final String pluriel;

    UniteDeDepot(String singulier, String pluriel) {
        this.singulier = singulier;
        this.pluriel = pluriel;
    }

    /// « archive », « séquence » ou « unité ».
    public String singulier() {
        return singulier;
    }

    /// « archives », « séquences » ou « unités ».
    public String pluriel() {
        return pluriel;
    }

    /// Le nom qui convient à un plan dont les unités ont ces types. Un plan vide se dit en archives,
    /// comme avant : rien n'y dément ce mot, et il ne s'affiche alors aucun compte.
    public static UniteDeDepot de(Collection<TypeDepotUnite> types) {
        Set<TypeDepotUnite> distincts = types.isEmpty() ? EnumSet.noneOf(TypeDepotUnite.class) : EnumSet.copyOf(types);
        if (distincts.size() > 1) {
            return UNITE;
        }
        return distincts.contains(TypeDepotUnite.WAV) ? SEQUENCE : ARCHIVE;
    }
}
