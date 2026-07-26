package fr.univ_amu.iut.commun.api;

/// Une **partie** d'un téléversement multipart S3 (#2354) : son numéro (1, 2, …) et l'`ETag` que S3 a
/// rendu à son `PUT`. La finalisation (`POST /fichiers/{id}` avec `{parts: […]}`) rassemble ces couples
/// pour que S3 recolle l'objet dans l'ordre - un `ETag` manquant ou mal ordonné casse le recollage.
///
/// @param partNumber numéro de la partie (à partir de 1, contigu)
/// @param etag `ETag` rendu par S3 au `PUT` de la partie (guillemets retirés)
public record PartieDeposee(int partNumber, String etag) {}
