package fr.univ_amu.iut.recette;

/// Comment le verdict se rend sur un cas de recette (#3764).
///
/// ## Pourquoi cet état existe
///
/// Le garde de #3728 ne connaissait que deux situations : un cas était **couvert**, ou il ne
/// l'était pas. Il en manquait une troisième, et l'omettre fabriquait un vert creux. Un scénario
/// qui **joue** un geste sans rien asserter cite pourtant son cas - c'est le seul lien vers le
/// script - et le garde le comptait aussitôt parmi les couverts. Le compteur annonçait alors un
/// cas prouvé que **personne n'avait jugé**.
///
/// ## Ce que chaque valeur engage
///
/// [#AUTOMATIQUE] est une promesse forte : quelque chose rougit quand le logiciel a tort. C'est
/// pourquoi elle est la valeur **par défaut** - non parce qu'elle serait la plus fréquente, mais
/// parce qu'un oubli doit alors se voir. Un test qui ne dit rien de son juge est réputé asserter,
/// et [CorrespondanceRecetteTest] rougit s'il porte sur un cas que le script marque perceptif.
///
/// [#HUMAIN] dit l'inverse : aucune assertion ne tranchera, il faut regarder. Le film n'entre pas
/// dans cette définition. Il rend le regard bon marché, il ne le remplace pas - et au moment où
/// cette énumération est écrite, aucun film n'existe encore.
///
/// ## ⚠️ Ce n'est pas la même chose que la marque du script
///
/// La marque `*perceptif*` d'un script qualifie le **cas** : on l'y pose en passe 6, quand on
/// constate qu'aucune assertion ne le tranchera, donc **avant** qu'un test existe. Cette
/// énumération qualifie le **test** : ce que ce code-ci prétend prouver. Les deux se recoupent, et
/// c'est précisément parce qu'ils se recoupent que [RepartitionDesCas] peut les confronter.
public enum Jugement {

    /// Une assertion tranche, et elle rougit quand le logiciel a tort.
    AUTOMATIQUE,

    /// Aucune assertion ne tranche : le cas se joue pour qu'un humain le regarde.
    HUMAIN
}
