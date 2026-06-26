package fr.univ_amu.iut.commun.view;

/// Contrat **optionnel** d'un écran central : être **rafraîchi quand on y revient**.
///
/// Le [Navigateur] garde les écrans **vivants** dans sa pile et les ré-affiche tels quels au retour
/// (← Retour ou clic d'un segment du fil d'Ariane en repli historique), pour préserver leur état. Or
/// un écran peut afficher des données qu'une **sous-activité** a modifiées pendant qu'il était masqué
/// — typiquement M-Passage, dont M-Qualification change le statut (verdict). Sans rechargement, le
/// retour montrerait un état **périmé** (le verdict ne serait pas visible).
///
/// Un écran concerné implémente ce contrat sur son `controller` (mémorisé par [EtapeNavigation], comme
/// [GardeQuitter] et [EmplacementNavigation]) : le [Navigateur] appelle alors [#rafraichirAuRetour()]
/// juste après l'avoir ré-affiché au sommet de la pile. Les écrans qui ne l'implémentent pas
/// conservent leur état inchangé (comportement par défaut).
public interface RafraichirAuRetour {

    /// Recharge les données de l'écran depuis la source de vérité. Appelé par le [Navigateur] sur le
    /// thread JavaFX, juste après avoir ré-affiché l'écran au sommet de la pile suite à un retour.
    void rafraichirAuRetour();
}
