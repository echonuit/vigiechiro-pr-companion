package fr.univ_amu.iut.cli;

import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;

/// Le geste que **la ligne de commande** propose pour lever un besoin (#2635), pendant de
/// `commun.viewmodel.GesteAttendu` côté application.
///
/// Ce que la CLI dit n'est pas une traduction du message de l'IHM, c'est **son** message : elle donne une
/// commande là où l'autre donne un chemin de menu. Avant #2635, elle répétait « menu ☰ > Se connecter » à
/// quelqu'un qui n'a pas de menu - le premier symptôme relevé par la parité CLI de #2554.
///
/// Les **fonctionnalités** font exception, et le message le dit franchement : elles se règlent dans
/// l'application, pas ici. Mieux vaut renvoyer à l'endroit qui existe que d'inventer une commande.
///
/// Cette phrase était écrite ici, et ce fichier l'enfreignait. Le besoin de connexion conseillait
/// « vigiechiro connexion --token <jeton> » : une commande qui **n'a jamais existé** (#3963). Le
/// vrai geste tient en trois voies : la variable `VIGIECHIRO_TOKEN`, la connexion enregistrée dans
/// l'application, et l'option `--token`. Il ne promet cette option à aucune commande en particulier :
/// `metadonnees-passage`, `reconstruire-passage` et `reactiver` lèvent ce besoin sans la porter.
///
/// Et « menu principal », pas le pictogramme ☰, pour la raison que l'ADR 3470 a fixée sur le
/// message d'incident : l'application se désigne partout de la même façon.
public final class GesteAttenduCli {

    private GesteAttenduCli() {}

    /// Le message complet d'un refus pour un terminal : ce qui manque, puis quoi taper. Un refus sans
    /// besoin est rendu tel quel.
    public static String message(Throwable refus) {
        String enonce = refus.getMessage() == null ? "Le geste est impossible." : refus.getMessage();
        if (!(refus instanceof RegleMetierException metier) || metier.besoin().isEmpty()) {
            return enonce;
        }
        return enonce + " " + quoiTaper(metier.besoin().orElseThrow());
    }

    private static String quoiTaper(Besoin besoin) {
        return switch (besoin) {
            case Besoin.Connexion ignore ->
                "Fournissez un jeton Vigie-Chiro, puis relancez : variable d'environnement VIGIECHIRO_TOKEN,"
                        + " connexion enregistrée dans l'application, ou option « --token <jeton> » quand la"
                        + " commande la propose.";
            case Besoin.Fonctionnalite fonctionnalite ->
                "Réactivez « " + fonctionnalite.nom()
                        + " » dans l'application (menu principal > Fonctionnalités) : les fonctionnalités ne se"
                        + " règlent pas en ligne de commande.";
        };
    }
}
