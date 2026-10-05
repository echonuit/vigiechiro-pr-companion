---
type: adr
title: "Le repli manuel s'offre après un refus sans recours, et n'est pas une étape"
status: stable
article: A23
heuristiques:
  - "nielsen-3"
  - "nielsen-9"
chantier: "#5867, lot 34 du chantier #5596 (retour de terrain 2.193.0)"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/DepotUniteDaoTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/view/EtapeDesArchivesTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/view/LotRepliManuelViewTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/viewmodel/SuiviLignesDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/cli/commande/DeposerVigieChiroTest.java"
  - "src/test/java/fr/univ_amu/iut/cli/commande/DeposerTest.java"
  - "src/test/java/fr/univ_amu/iut/audit/model/ServiceRecuperabiliteTest.java"
verification_note: "la première classe tient la règle cause par cause sur le plan enregistré dans une vraie base ; la deuxième, le repli offert ou non selon la forme, la connexion et le statut ; la troisième fait refuser un téléversement à l écran, trouve la carte sous celle du téléversement et clique son dernier geste ; la quatrième, le refus définitif gardé au rechargement ; les trois dernières, la sortie des deux commandes et le verdict de récupérabilité. Aucune ne dit ce que le portail fait d une archive en partie déjà en ligne : c est une hypothèse, nommée plus bas"
relations:
  complete: ["5677-le-depot-part-en-wav-et-un-depot-entame-garde-son-mode", "5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Le repli manuel s'offre après un refus sans recours, et n'est pas une étape

## Contexte

L'[ADR 5824](5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert.md) a retiré de l'écran
de lot, connecté et en séquences WAV, l'étape des archives et le dépôt manuel. Elle en consignait la
conséquence : un téléversement en échec n'offre plus de repli.

Or un refus peut être sans recours. Depuis l'[ADR 5598](5598-la-provenance-d-un-refus-remonte-par-le-depot.md),
un refus définitif porte sa cause : les droits, que la reconnexion réarme ; le contenu, que rien ne change
pour une séquence ; le stockage, qu'une relance ne lève pas toujours. Dans les deux derniers cas l'écran
n'offrait aucune voie.

Le porteur a tranché le 5 octobre 2026, sur #5867 : le repli revient **après un refus**, **en archives ZIP
déposées à la main**.

## Décision

**1. Le repli s'offre quand au moins une séquence est refusée sans recours** : définitivement, et pour une
cause autre que les droits. Un échec que la reprise peut lever ne l'offre pas. Une ligne antérieure à la
migration V41, définitive et sans cause, compte : elle ne se réarme pas.

**2. La règle se lit dans le plan de dépôt enregistré, à un seul endroit.**
`ServiceLot.sequencesRefuseesSansRecours` la rend à l'écran comme à la commande
([ADR 0014](0014-parite-cli-ihm.md)), et reçoit la cause qu'une reconnexion lève de la même façon que le
réarmement. Le repli tient donc à la réouverture de l'écran.

**3. Le repli n'est pas une étape.** Le fil reste à trois, les numéros ne bougent pas. La carte des
archives reparaît **sous** celle du téléversement, sans numéro, sous le titre « Repli : déposer à la main ».
La vue distingue deux questions qui n'en faisaient qu'une : le **numéro** des étapes, et la **présence** de
la carte et du dépôt manuel.

**4. Le dépôt que l'application téléverse garde sa forme.** Les archives du repli sont générées sur le
disque et partent à la main : elles n'entrent pas dans le plan, qui reste en séquences WAV.

**5. Le dernier geste a deux portes.** Dès qu'une participation est liée, la dernière étape lance la
participation et ne marque plus rien : un dépôt fini à la main restait « Dépôt en cours ». La carte du
repli porte donc « Marquer le passage déposé », actif une fois les archives générées. Et `deposer` ne
prépare que ce qui ne l'est pas, comme `exporter-lot` depuis
l'[ADR 5599](5599-un-depot-entame-se-regenere-sauf-pendant-un-televersement.md) : il refusait un dépôt
entamé que le moteur autorise à passer « Déposé ».

## Deux corrections que le repli rendait nécessaires

**Rechargée depuis le plan, la table garde le caractère définitif d'un refus.** Elle l'oubliait, et le
bouton promettait à chaque réouverture la reprise que #3687 avait retirée, juste au-dessus du repli.

**Le serveur n'est dit dépositaire que de ce qu'il a reçu.** Le bilan de récupérabilité lisait le type des
unités, pas leur statut : une séquence refusée comptait comme gardée. Après un repli elle est partie en
archive, dont la plateforme ne garde pas l'audio. Il faut désormais que toutes les unités soient déposées
en WAV ; sinon le motif compte celles qui manquent.

## Ce que cette décision change aux ADR 5677 et 5824

La règle 3 de l'[ADR 5677](5677-le-depot-part-en-wav-et-un-depot-entame-garde-son-mode.md), « un dépôt
entamé garde le mode de ce qui est déjà en ligne », tient pour ce que l'application téléverse. Elle ne dit
plus toute la participation : après un repli, celle-ci porte des séquences **et** des archives.

L'ADR 5824 tient, sauf sa conséquence. Sa règle « n'offrir que ce qui sert » est appliquée : après un refus
sans recours, les archives servent.

## Ce qui a été écarté

**Déposer à la main les séquences WAV elles-mêmes.** C'était la voie recommandée ; le porteur a choisi les
archives.

**Revenir à quatre étapes.** « 2. Générer les archives » se serait placé avant l'étape refusée, et l'écran
se serait renuméroté au moment où l'utilisateur est en difficulté.

**Offrir le repli dès un dépôt incomplet.** Un échec que la reprise lève n'a pas besoin d'une autre voie.

**Poser le compte sur `LotViewModel`.** Il franchissait le plafond `GodClass`, ce que l'ADR 5824 annonçait :
le compte vit sur le modèle de vue du dépôt, dont c'est l'état.

## Conséquences

Les archives du repli contiennent **toute la nuit**, séquences déjà en ligne comprises : le générateur n'est
pas touché. Ce que le portail fait d'une archive dont une partie des sons est déjà dans la participation
n'est **pas établi** ; c'est une hypothèse de ce lot, acceptée comme telle par le porteur.

Une nuit finie par le repli est annoncée perdue par le bilan de récupérabilité si son disque ne suffit pas,
avec le compte de ce qui manque.
