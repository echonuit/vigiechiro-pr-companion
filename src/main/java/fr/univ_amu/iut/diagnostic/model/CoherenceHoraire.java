package fr.univ_amu.iut.diagnostic.model;

import java.time.LocalTime;

/// Ce que la nuit enregistrée doit au protocole Vigie-Chiro Point Fixe, et ce qu'elle lui rend.
///
/// Le protocole demande de commencer **au moins** 30 minutes avant le coucher du soleil et de finir
/// **au moins** 30 minutes après son lever. C'est un **plancher** : une plage plus large le couvre,
/// et le dépasser n'est pas s'en écarter.
///
/// Ce type portait auparavant deux booléens, `demarrageHorsNuit` et `arretHorsNuit`, qui répondaient
/// à la question « est-on hors de la nuit ». C'était la mauvaise question, et elle a produit un
/// dispositif qui alertait sur le respect du protocole et se taisait sur sa violation (#4987).
///
/// Ce type ne dit que ce que les **horaires** montrent. Un troisième niveau était prévu pour une nuit
/// que le capteur a consignée interrompue ; il est reporté, la complétude d'une nuit n'étant persistée
/// nulle part et le diagnostic ne pouvant donc pas la retrouver. Ne pas le rendre vaut mieux que le
/// feindre : l'absence d'avertissement ne prouve pas qu'une nuit est entière.
///
/// @param disponible `true` si la fenêtre a pu être calculée
/// @param coucherSoleil heure **locale** du coucher, ou `null` si indisponible
/// @param leverSoleil heure **locale** du lever, ou `null` si indisponible
/// @param debutExige heure à laquelle le protocole demande d'avoir commencé, ou `null`
/// @param finExigee heure jusqu'à laquelle le protocole demande d'avoir enregistré, ou `null`
/// @param debutEnregistre heure de début réellement enregistrée, ou `null`
/// @param finEnregistree heure de fin réellement enregistrée, ou `null`
/// @param couverture ce que les horaires disent de la fenêtre exigée
public record CoherenceHoraire(
        boolean disponible,
        LocalTime coucherSoleil,
        LocalTime leverSoleil,
        LocalTime debutExige,
        LocalTime finExigee,
        LocalTime debutEnregistre,
        LocalTime finEnregistree,
        Couverture couverture) {

    /// Ce que les horaires disent de la fenêtre que le protocole exige.
    ///
    /// Les valeurs nomment l'**état du domaine**, jamais la gravité de son annonce. Le premier jet
    /// disait `INFORMATION` et `AVERTISSEMENT`, c'est-à-dire deux synonymes de [Severite], et les
    /// deux surfaces les convertissaient une pour une : c'était une seconde échelle de sévérité
    /// dans le modèle, exactement le motif contre lequel l'ADR 0038 met en garde. La gravité est
    /// une décision de surface, et elle se prend là où l'on parle à quelqu'un.
    public enum Couverture {
        /// La vérification n'a pas pu être faite : ni GPS, ni horaires, ou nuit polaire.
        INDISPONIBLE,
        /// La fenêtre exigée est couverte. Un fait, pas un défaut : le protocole est un plancher.
        ///
        /// Aucune valeur ne distingue « couverte tout juste » de « couverte et dépassée ». L'égalité
        /// à la seconde près n'arrive pas, et un niveau qu'aucune nuit n'atteint serait un niveau
        /// mort.
        COUVERTE,
        /// La fenêtre n'est pas entièrement couverte : le protocole n'est pas tenu.
        INCOMPLETE
    }

    private static final CoherenceHoraire INDISPONIBLE =
            new CoherenceHoraire(false, null, null, null, null, null, null, Couverture.INDISPONIBLE);

    /// Instance signalant qu'aucune vérification n'a pu être faite (GPS/horaires absents ou latitude
    /// polaire).
    public static CoherenceHoraire indisponible() {
        return INDISPONIBLE;
    }
}
