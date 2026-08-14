package fr.univ_amu.iut.recette;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marque une classe qui **imite** un test de recette pour éprouver l'outillage, sans rien couvrir
/// (#3774).
///
/// ## Le défaut qu'elle corrige, et qui a été vu rouge
///
/// Éprouver [ReperesDeSeance] demande de vraies classes portant de vraies annotations
/// [CasDeRecette] : c'est la seule façon de vérifier que le moteur JUnit les voit. Mais
/// [CorrespondanceRecetteTest] balaie le même classpath, et il a compté ces exemples comme des
/// **citations réelles**. Le premier passage en CI a rougi sur le bon motif : un exemple citait
/// `S1-26`, que le script marque `*perceptif*`, et le garde y a vu du code prétendant asserter ce
/// qu'aucune assertion ne tranche.
///
/// Le garde avait raison. Ce ne sont pas des tests du produit : ils ne montrent rien qu'on irait
/// regarder, et les compter gonflerait l'index de couverture de tests qui n'exercent que
/// l'outillage.
///
/// ## ⚠️ Ce qu'elle ne doit jamais servir à faire
///
/// La poser sur un **vrai** test le retirerait du recensement en silence, et son cas retomberait
/// dans les non couverts sans que personne comprenne pourquoi. Elle ne se pose que sur des classes
/// qui n'exercent pas le produit.
///
/// L'exclusion qu'elle commande est **portante** : l'exemple de [ReperesDeSeanceTest] cite
/// délibérément `S1-26`, si bien que retirer l'exclusion fait immédiatement rougir le build. Elle ne
/// peut donc pas se périmer sans qu'on le sache.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FixtureDeRecette {}
