package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import java.util.Objects;

/// Ce qu'un écran de données **mémorise entre deux visites** : les critères et le tri, les vues
/// nommées par l'utilisateur, et la disposition de ses colonnes.
///
/// ## Pourquoi les trois voyagent ensemble
///
/// Ils répondent à une seule question - *que retrouve-t-on en rouvrant l'écran ?* - et aucun écran
/// n'en prend un sans prendre les autres. Les passer séparément faisait de trois paramètres ce qui est
/// une seule idée, et l'a rappelé de la façon la plus concrète : `MultisiteController` était à onze
/// paramètres, le seuil exact au-delà duquel le portail qualité attend un objet-paramètre.
///
/// ## Qui le prend, et qui ne le prend pas
///
/// Les deux écrans qui portent les **trois** : Carte & passages, et Espèces & observations.
///
/// Activité de la nuit et Audit de cohérence mémorisent bien leurs filtres et leurs vues, mais **n'ont
/// pas de colonnes à ranger** - c'est un fait de ces écrans, pas un oubli. Ils gardent donc leurs deux
/// collaborateurs, et restent loin du seuil.
public final class MemoireEcran {

    private final MemoireFiltres filtres;
    private final DepotVues vues;
    private final DepotDispositionColonnes colonnes;

    @Inject
    public MemoireEcran(MemoireFiltres filtres, DepotVues vues, DepotDispositionColonnes colonnes) {
        this.filtres = Objects.requireNonNull(filtres, "filtres");
        this.vues = Objects.requireNonNull(vues, "vues");
        this.colonnes = Objects.requireNonNull(colonnes, "colonnes");
    }

    /// Les critères actifs et le tri, reposés à la réouverture de l'écran.
    public MemoireFiltres filtres() {
        return filtres;
    }

    /// Les vues sauvegardées, celles que l'utilisateur a nommées lui-même.
    public DepotVues vues() {
        return vues;
    }

    /// La disposition des colonnes : lesquelles sont visibles, dans quel ordre, à quelle largeur.
    public DepotDispositionColonnes colonnes() {
        return colonnes;
    }
}
