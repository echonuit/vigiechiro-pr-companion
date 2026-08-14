package fr.univ_amu.iut.recette;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Déclare qu'un test couvre un ou plusieurs cas d'une session de recette (#3728).
///
/// ## Pourquoi une annotation, et non un `@DisplayName` préfixé
///
/// Le dépôt emploie `@DisplayName` partout, et l'y préfixer aurait évité un type de plus. Mais ce
/// lien existe précisément parce qu'un document tenu à la main a **déjà dérivé** :
/// `s1-premier-contact.md` désignait les cas 24 et 25 là où il parlait manifestement des 26 et 27,
/// sans doute après une renumérotation. Faire reposer la traçabilité sur du texte libre, qu'une
/// reformulation casse sans rien signaler, l'aurait replacée là où elle a lâché.
///
/// ## Ce que l'annotation ne dit PAS
///
/// Elle ne prétend pas que le cas est *entièrement* couvert. Un cas de recette vaut souvent
/// plusieurs assertions - `S1-18` en contient six à lui seul (« carré, département, protocole,
/// créé le, dernière nuit, passages »). Elle dit : « ce test participe à la couverture de ce
/// cas ».
///
/// ## Ce qui la garde
///
/// [CorrespondanceRecetteTest] vérifie **dans les deux sens** : tout identifiant cité existe dans
/// une session, et les cas que rien ne couvre sont **listés**. Le second devoir est celui qu'on
/// oublie, et sans lui un garde silencieux est indiscernable d'un garde qui ne lit rien.
///
/// Exemple :
/// ```java
/// @Test
/// @CasDeRecette("S1-02")
/// @DisplayName("Le bandeau de compteurs est masqué sans donnée")
/// void bandeau_masque_sans_donnee(FxRobot robot) { ... }
/// ```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CasDeRecette {

    /// Les identifiants couverts, tels qu'ils s'écrivent dans la session (`S1-04`).
    String[] value();
}
