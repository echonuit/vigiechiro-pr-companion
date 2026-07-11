package fr.univ_amu.iut.commun.view;

import java.util.List;

/// Description **déclarative** d'un réglage : donnée pure, sans JavaFX. Une feature en fournit la
/// liste via [OngletReglages#reglages()] ; le socle (`ControleursReglages`) en dérive le contrôle
/// d'IHM adéquat (case, spinner, champ, liste déroulante) et le câble à `ReglagesReactifs` sur la
/// [#cle()].
///
/// Type **scellé** : l'ensemble des formes de réglage est fermé et connu du socle, ce qui rend le
/// rendu exhaustif et vérifiable à la compilation.
public sealed interface DescripteurReglage
        permits DescripteurReglage.Booleen,
                DescripteurReglage.Entier,
                DescripteurReglage.Texte,
                DescripteurReglage.Enumeration {

    /// Clé de persistance dans la table `app_setting` (ex. `"import.conserver-originaux"`).
    String cle();

    /// Libellé affiché à côté du contrôle.
    String libelle();

    /// Aide contextuelle (tooltip). Chaîne vide si aucune.
    String aide();

    /// Réglage **booléen** rendu en case à cocher.
    record Booleen(String cle, String libelle, String aide, boolean defaut) implements DescripteurReglage {}

    /// Réglage **entier borné** rendu en spinner (`[min, max]`).
    record Entier(String cle, String libelle, String aide, int defaut, int min, int max)
            implements DescripteurReglage {}

    /// Réglage **texte libre** rendu en champ de saisie.
    record Texte(String cle, String libelle, String aide, String defaut) implements DescripteurReglage {}

    /// Réglage à **choix fermé** rendu en liste déroulante. La valeur persistée est l'[Option#valeur()]
    /// sélectionnée ; `defaut` est l'une de ces valeurs.
    record Enumeration(String cle, String libelle, String aide, List<Option> options, String defaut)
            implements DescripteurReglage {

        /// Une entrée du choix : valeur **persistée** (`valeur`) et texte **affiché** (`libelle`).
        public record Option(String valeur, String libelle) {}
    }
}
