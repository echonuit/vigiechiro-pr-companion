package fr.univ_amu.iut.commun.api;

import fr.univ_amu.iut.commun.model.LectureBornee;
import fr.univ_amu.iut.commun.model.PlafondLecture;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

/// Corps d'une réponse HTTP, lu **sous plafond** (#3222).
///
/// Les trois clients HTTP du produit - la plateforme VigieChiro, GBIF et l'API Géo - chargeaient
/// chacun la réponse entière avant de savoir si elle était exploitable. C'est la même forme de défaut
/// que la décompression non bornée de #2732, dans un autre canal : rien ne distinguait une réponse
/// normale d'une réponse aberrante avant de l'avoir avalée.
///
/// **Deux gardes**, pour la même raison qu'à l'extraction d'une archive : le premier lit ce que le
/// serveur **annonce** (`Content-Length`) et refuse sans lire un octet ; le second compte ce qu'il
/// **envoie**, car une réponse en encodage par blocs n'annonce aucune taille. Un `Content-Length`
/// absent vaut `-1`, qui ne franchit aucun plafond : c'est bien le second garde qui tient ce cas.
///
/// Le corps est lu **quel que soit le statut** : une réponse d'erreur porte souvent le message le plus
/// utile (le transport le remonte dans son refus), et la lire ici garantit que le flux est refermé sur
/// tous les chemins, y compris le refus.
final class CorpsReponse {

    private CorpsReponse() {}

    /// @param origine ce qu'on lisait, tel que le refus le nomme (chemin d'appel, service interrogé)
    /// @throws fr.univ_amu.iut.commun.model.EntreeTropVolumineuse si le corps franchit le plafond
    /// @throws IOException si le flux est illisible
    static String sousPlafond(HttpResponse<InputStream> reponse, String origine) throws IOException {
        PlafondLecture plafond = PlafondLecture.corpsReseau();
        try (InputStream flux = reponse.body()) {
            plafond.exiger(reponse.headers().firstValueAsLong("Content-Length").orElse(-1), origine);
            return LectureBornee.texte(flux, plafond, origine);
        }
    }
}
