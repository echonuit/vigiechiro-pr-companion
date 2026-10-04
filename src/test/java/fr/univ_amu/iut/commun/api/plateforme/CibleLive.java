package fr.univ_amu.iut.commun.api.plateforme;

/// Vers quelle plateforme jouent les sondes live, et ce qu'elles y ont le droit d'écrire (#5746).
///
/// Une sonde **déclare** sa cible (ADR 5641), par la propriété `vigiechiro.cible` que pose le profil
/// `-Pplateforme-de-test`. Elle ne la déduit pas de l'absence de jeton : un oubli passerait pour un
/// choix, et une suite censée jouer sur la plateforme nationale jouerait ailleurs sans le dire.
///
/// - **plateforme de test** : l'URL, le jeton et les participations viennent de
///   [PlateformeDeTest#acces()], et les verrous d'écriture sont ouverts, rien n'y étant à abîmer ;
/// - **plateforme nationale**, par défaut : les propriétés système et les verrous d'avant, inchangés.
///
/// @param urlDeBase l'URL de l'API, `/api/v1` compris
/// @param jeton le jeton de l'observatrice, ou `null` quand la nationale n'en a pas reçu
/// @param ecritureOuverte vrai quand les sondes d'écriture peuvent tourner
/// @param messageOuvert vrai quand la sonde de message, dont l'écriture est définitive, peut tourner
/// @param participationRebut une participation où lancer un calcul sans conséquence, ou `null`
/// @param participationEssai une participation où déclarer des fichiers et réécrire la configuration,
///     ou `null`
public record CibleLive(
        String urlDeBase,
        String jeton,
        boolean ecritureOuverte,
        boolean messageOuvert,
        String participationRebut,
        String participationEssai) {

    /// La valeur de `vigiechiro.cible` qui désigne la plateforme de test.
    public static final String PLATEFORME_DE_TEST = "plateforme-de-test";

    private static final String NATIONALE = "https://vigiechiro.herokuapp.com/api/v1";

    /// Vrai quand le profil a déclaré la plateforme de test : la seule lecture de `vigiechiro.cible`,
    /// que le banc de recette partage avec les sondes (#5793).
    public static boolean plateformeDeTestDeclaree() {
        return PLATEFORME_DE_TEST.equals(System.getProperty("vigiechiro.cible"));
    }

    /// La cible que le profil a déclarée.
    public static CibleLive declaree() {
        if (plateformeDeTestDeclaree()) {
            PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
            return new CibleLive(
                    acces.urlDeBase(),
                    acces.jeton("observatrice"),
                    true,
                    true,
                    acces.id("participations:nuit-vierge"),
                    acces.id("participations:nuit-traitee"));
        }
        return new CibleLive(
                System.getProperty("vigiechiro.url", NATIONALE),
                System.getProperty("vigiechiro.token"),
                Boolean.getBoolean("vigiechiro.write"),
                Boolean.getBoolean("vigiechiro.message"),
                System.getProperty("vigiechiro.participationRebut"),
                System.getProperty("vigiechiro.participationEssai"));
    }
}
