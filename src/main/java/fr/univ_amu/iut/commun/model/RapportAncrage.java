package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/// **Ce que la phase d'ancrage a rapporté**, prêt à être joint au compte rendu du geste qui l'a
/// déclenchée (ADR 0019).
///
/// Deux gestes acquièrent l'ancrage manquant d'une nuit, chacun au moment où il sert : la **publication
/// des corrections** (#1838) et la **réactivation** d'un passage reconstruit (#1571, #1904). Tous deux
/// en rapportent la même chose - un texte à afficher, qui dit notamment combien d'observations ont reçu
/// un **échange avec le validateur** (#1867) - et tous deux doivent savoir **se taire** quand la phase
/// n'a pas eu lieu, ce qui est le cas courant.
///
/// C'est ce silence qui justifie ce type. Sans lui, la convention « chaîne vide = rien à dire » se
/// vérifiait par un `isBlank()` recopié à **quatre** endroits : deux ViewModels et deux commandes. Un
/// test recopié s'oublie ou s'inverse ; [#estMuet()] le nomme une fois.
///
/// Le texte vient du port [ImportObservations], qui le construit : le bilan détaillé de l'import
/// appartient à la feature `validation` et n'a pas à traverser le socle. Ce type transporte donc un
/// **texte**, jamais un bilan.
///
/// Nommé pour ce qu'il porte **aujourd'hui**, l'ancrage. Si une autre phase annexe vient un jour rendre
/// compte de la même façon, ce sera le moment de généraliser le nom - pas avant.
///
/// @param texte le compte rendu, prêt à afficher ; **vide** quand la phase n'a rien eu à acquérir
public record RapportAncrage(String texte) {

    public RapportAncrage {
        Objects.requireNonNull(texte, "texte");
    }

    /// Aucune phase d'ancrage n'a eu lieu : il n'y a rien à annoncer.
    public static RapportAncrage aucun() {
        return new RapportAncrage("");
    }

    /// N'y a-t-il **rien à dire** ? Vrai quand la phase ne s'est pas déclenchée, ou n'a rien rapporté.
    ///
    /// Les surfaces s'en servent pour **omettre** la ligne plutôt que d'en afficher une vide : annoncer
    /// « rien n'a été fait » après un geste qui n'a rien coûté serait du bruit.
    public boolean estMuet() {
        return texte.isBlank();
    }
}
