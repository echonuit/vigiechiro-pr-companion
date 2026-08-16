package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.Protocole;
import java.util.Objects;

/// Ce que l'utilisateur avait rempli dans la modale de déclaration quand il a découvert que le carré
/// existait déjà (#3806).
///
/// ## Pourquoi ces champs voyagent ensemble
///
/// Le rapatriement se déclenche **au milieu d'une saisie** : l'utilisateur venait de déclarer son carré,
/// il a vérifié, et le carré était là-bas. Ce qu'il avait écrit n'a pas à disparaître au passage - le
/// site rapatrié prendrait sinon le **titre de la plateforme** (`Vigiechiro - Point Fixe-130711`), un
/// libellé technique, à la place du nom que l'utilisateur venait de choisir.
///
/// Ils sont regroupés plutôt que passés un à un : quatre paramètres de même type (trois chaînes et un
/// énuméré) s'intervertissent sans que le compilateur bronche, et l'arité des constructions est un
/// travers que le dépôt corrige plutôt qu'il n'étend (EPIC #2483).
///
/// @param numeroCarre le carré cherché, six chiffres
/// @param protocole le protocole choisi : il décide **quel site** récupérer quand le carré en porte
///     plusieurs, et sous quelle variante locale le site est créé
/// @param nomConvivial le nom saisi, ou `null`/vide s'il n'y en a pas - le titre plateforme sert alors
/// @param commentaire les notes saisies, ou `null`/vide
public record SouhaitDeclaration(String numeroCarre, Protocole protocole, String nomConvivial, String commentaire) {

    public SouhaitDeclaration {
        Objects.requireNonNull(numeroCarre, "numeroCarre");
        Objects.requireNonNull(protocole, "protocole");
    }

    /// Le nom à donner au site rapatrié : celui de l'utilisateur s'il en a écrit un, sinon le titre de la
    /// plateforme. Un nom vaut mieux que pas de nom - à défaut du sien, celui-là dit au moins de quel
    /// carré et de quel protocole il s'agit.
    public String nomOuTitre(String titrePlateforme) {
        return nomConvivial == null || nomConvivial.isBlank() ? titrePlateforme : nomConvivial;
    }

    /// Les notes de l'utilisateur, ou `null` : un champ facultatif laissé vide vaut `null` en base, pas
    /// chaîne vide.
    public String commentaireOuNull() {
        return commentaire == null || commentaire.isBlank() ? null : commentaire;
    }
}
