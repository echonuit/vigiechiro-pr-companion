---
type: adr
title: "La plateforme de test pose l'état de la JVM sans le rendre, dans un fork qui n'est qu'à elle"
status: stable
article: A4
chantier: "#5663, clôture de l'EPIC #5642 (chantier #5640)"
decided_at: 2026-10-01
verification: certaine
enforced_by:
  - "DeclarationDeLaPlateformeTest#une_classe_qui_monte_la_plateforme_de_test_porte_son_tag"
  - "DeclarationDeLaPlateformeTest#le_build_par_defaut_exclut_la_plateforme_de_test"
verified:
  - by: humain
    at: 2026-10-01
relations:
  amende: ["4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien"]
  prolonge: ["5641-les-tests-connectes-ont-deux-cibles"]
generated:
  by: "process:assistance-par-agents"
---

# La plateforme de test pose l'état de la JVM sans le rendre, dans un fork qui n'est qu'à elle

## Le contexte

L'[ADR 4134](4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md) pose qu'**un banc
n'emprunte pas l'état partagé, il ouvre le sien**, parce qu'un état laissé derrière soi sert à toutes
les classes qui passent ensuite dans le même fork, et que le symptôme apparaît alors loin de sa cause.

`PlateformeDeTest` (#5663) ne peut pas ouvrir le sien. Pour que Companion dépose sur le faux S3 en
TLS, la JVM doit faire confiance à son certificat et admettre son hôte comme destination d'une URL
signée. Elle écrit donc deux états de processus :

- `SSLContext.setDefault(...)`, parce que `TransportVigieChiro` construit son `HttpClient` sans
  contexte, donc sur celui par défaut, lu à la construction du client ;
- la propriété `vigiechiro.s3.hotes`, que lit `UrlSigneeAdmise`.

Et elle ne les rend pas : la plateforme est montée une fois par JVM, au premier test qui la demande,
et partagée par les suivants jusqu'à la fin du processus.

## La décision

**Le fork est l'état que la plateforme ouvre.** Elle écrit l'état de la JVM sans le restaurer, et ce
n'est tenable que parce qu'aucune classe étrangère ne partage ce processus :

- toute classe qui monte la plateforme porte `@Tag("plateforme-de-test")`, qu'elle la monte par la
  déclaration du banc (`surLaPlateformeDeTest("...")`) ou par l'extension
  (`@ExtendWith(PlateformeDeTest.class)`) ;
- le build par défaut exclut ce tag, dans `surefire.excludedGroups` ;
- le profil `-Pplateforme-de-test` ne joue que lui, et le job du même nom ne lance que ce profil.

La règle de 4134 tient donc, un cran plus haut : ce n'est plus la fenêtre ni le test qui sont à soi,
c'est le processus.

**Une conséquence pour qui écrit un test du tag** : un client construit **avant** le montage garde
l'ancien contexte. Un test de ce tag ne construit donc aucun client Vigie-Chiro avant d'avoir demandé
`PlateformeDeTest.acces()`, ce que font l'extension et le banc.

## Ce qui la tient

`DeclarationDeLaPlateformeTest` porte les deux moitiés. Le premier cas refuse une classe qui monte la
plateforme sans le tag ; il ne cherchait que la déclaration du banc et a été élargi à l'extension à la
clôture de #5642, `DepotSurLaPlateformeDeTestTest` étant écrit ainsi. Le second refuse un build par
défaut qui n'exclurait plus le tag, ce que rien ne faisait rougir avant la même clôture : seuls les
runners Windows et macOS du mardi auraient cassé, faute de Docker.

Les deux sont vus rouges par mutation : le tag retiré de `DepotSurLaPlateformeDeTestTest`, puis le tag
retiré de `surefire.excludedGroups`. Chaque motif du premier cas doit aussi trouver au moins une
classe, sans quoi un motif qui ne correspond plus serait masqué par l'autre.

## Ce qui a été écarté

**Donner à `TransportVigieChiro` un client ou un contexte à soi**, pesé dans #5663. Ce serait la
forme « ouvrir le sien » au sens strict. Mais un `HttpClient` ne s'injecte que depuis le paquet
`commun.api`, et changer la production pour ce seul besoin élargirait sa surface pour la commodité
d'un test, ce que le dépôt refuse déjà pour l'URL du client (#4332).

**Restaurer à la fin**, examiné à la clôture. La plateforme sert toute la JVM, donc la restauration attendrait la fin du
processus, où elle ne sert plus à personne. Et un client construit entre-temps garderait son contexte
quoi qu'on restaure : l'ordre resterait la vraie dépendance. C'est la raison même que 4134 donnait
contre « restaurer mieux ».

**Un magasin de confiance posé au lancement de la JVM** (`-Djavax.net.ssl.trustStore`), examiné à la
clôture. Le certificat
du faux S3 naît au démarrage de son conteneur, avec les noms de l'hôte que Testcontainers rend : il
n'existe pas encore quand la JVM démarre.
