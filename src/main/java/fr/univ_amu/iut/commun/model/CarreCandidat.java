package fr.univ_amu.iut.commun.model;

/// Un carré candidat pour une position : son numéro, et la distance de la position au **centre** de sa
/// maille (#4621).
///
/// La distance sert à départager. Sur une frontière, deux mailles sont à distance strictement égale, et
/// quatre le sont à un coin : mesuré le 2026-08-27, 997,7 m au milieu d'un côté et 1 412 m au coin.
/// C'est l'appelant qui décide quoi faire de cette égalité, pas cette lecture.
///
/// Le `numero` porte ses **six chiffres**, zéro de gauche compris : le référentiel l'ampute dans les
/// départements 01 à 09, et [NumeroDeCarre] le rétablit avant que ce type n'existe.
public record CarreCandidat(String numero, double distanceMetres) {}
