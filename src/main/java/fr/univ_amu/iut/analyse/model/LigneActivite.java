package fr.univ_amu.iut.analyse.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/// Une ligne d'**export** de l'activité : le nombre de contacts d'une espèce, dans une tranche horaire,
/// **en un lieu et une nuit donnés** (#2613).
///
/// La courbe et l'export ne demandent pas la même matière. Une [CourbeEspece] est une fonction du temps
/// pour **une** espèce, tous lieux confondus : y loger le carré et le point n'aurait pas de sens, une
/// espèce pouvant être contactée sur plusieurs carrés la même nuit. Un export, lui, se recoupe et se
/// filtre dans un tableur : chacune de ses lignes doit **porter son contexte entier**, faute de quoi un
/// export multi-nuits ne dit plus d'où vient ce qu'il montre.
///
/// D'où deux agrégations sur les mêmes contacts : [AgregationActivite#parEspece] pour tracer,
/// [AgregationActivite#pourExport] pour écrire.
///
/// @param numeroCarre carré Vigie-Chiro du point d'écoute, ou `null` s'il n'est pas connu
/// @param codePoint point d'écoute, ou `null`
/// @param nuit nuit biologique de la tranche (bascule à midi), jamais `null`
/// @param taxon code du taxon retenu
/// @param nomEspece nom vernaculaire, ou `null`
/// @param groupe catégorie taxonomique, ou `null`
/// @param debutTranche début de la tranche, aligné sur l'horloge, instant réel
/// @param nombre nombre de contacts dans la tranche, toujours strictement positif
public record LigneActivite(
        String numeroCarre,
        String codePoint,
        LocalDate nuit,
        String taxon,
        String nomEspece,
        String groupe,
        LocalDateTime debutTranche,
        int nombre) {}
