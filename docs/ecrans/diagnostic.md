# Diagnostic

Le **Diagnostic matériel** fait le point sur l'état du capteur pour une nuit donnée. C'est une
action transverse, accessible depuis l'écran [Passage](passage.md) quel que soit l'avancement.

![L'écran de diagnostic : courbe climatique, anomalies, évènements et cohérence horaire.](../assets/captures/apercu-diagnostic.png)

L'écran réunit :

- la **température en début de nuit** et une **courbe climatique** de la nuit (température et
  hygrométrie), dont l'axe est gradué en heures ;
- les **anomalies** détectées (par exemple réveil non programmé, batterie faible) ;
- les **évènements du journal** du capteur (démarrage, arrêt programmé...) ;
- la **cohérence horaire** : la fenêtre nocturne réelle au point d'écoute (heures de coucher et de
  lever du soleil), la fenêtre que le protocole exige de couvrir, la plage réellement enregistrée, et
  un mot sur l'écart entre les deux ;
- la **fenêtre nocturne** matérialisée par un aplat pâle derrière la courbe : elle situe les mesures
  dans la nuit réelle, et sans coordonnées au point elle disparaît plutôt que d'être inventée ;
- un bouton **Exporter le graphe…** qui écrit la courbe en image, **redessinée** pour l'occasion (donc
  fidèle même fenêtre réduite) et portant son contexte : carré, point, passage, nombre de mesures,
  version et date. C'est ce qu'on joint à un signalement d'anomalie ;
- l'état du **GPS du point d'écoute** : « disponible », ou « non renseigné » avec une invitation à
  compléter la fiche du site. Ce sont les coordonnées du point d'écoute (et non du capteur) ; le
  calcul de la fenêtre nocturne en dépend.

L'enregistreur diagnostiqué et le nombre de mesures climatiques figurent dans la **barre de statut**
en bas de la fenêtre.

## La fenêtre que le protocole exige

Le protocole Vigie-Chiro Point Fixe demande d'enregistrer **au moins** de 30 minutes avant le coucher
du soleil à 30 minutes après son lever. C'est un **plancher** : commencer plus tôt ou finir plus tard
couvre ce qui est demandé, et davantage.

Cette marge est voulue. Elle produit bien une portion d'enregistrement en plein jour, et c'est le but :
les premières chauves-souris sortent avant la nuit complète, et les dernières rentrent après l'aube.

L'écran montre donc deux plages sous la fenêtre nocturne : ce que le protocole attendait, et ce que
vous avez enregistré.

- Si votre plage **couvre** la fenêtre exigée, une information vous le dit. Ce n'est pas un défaut :
  c'est le cas de l'aperçu en tête de page, où l'encart est bleu et non ambre.
- Si elle **ne la couvre pas**, un avertissement le signale : une partie de la nuit demandée n'a pas
  été enregistrée. Vérifiez le paramétrage de l'enregistreur pour la sortie suivante.

![L'écran de diagnostic quand la fenêtre du protocole n'est pas couverte : l'avertissement nomme la
plage attendue et la plage enregistrée.](../assets/captures/apercu-diagnostic-protocole-non-couvert.png)

L'écran ne signale pas les interruptions survenues **au milieu** d'une nuit : cette information n'est
pas conservée après l'import. L'absence d'avertissement ne prouve donc pas qu'une nuit est entière.

## Sans relevé climatique

Si la nuit ne comporte pas de relevé climatique, l'absence est signalée et seules les anomalies et
les évènements du journal restent affichés.

![L'écran de diagnostic sans relevé climatique : l'absence est signalée.](../assets/captures/apercu-diagnostic-sans-releve.png)

## Sans coordonnées GPS

Si le point d'écoute n'a pas de coordonnées, le repère GPS passe à « non renseigné (compléter la
fiche site) » et l'encart cohérence horaire disparaît : la fenêtre nocturne ne peut pas être calculée
sans coordonnées. Le reste du diagnostic (courbe, anomalies, évènements) demeure exploitable.

![L'écran de diagnostic sans coordonnées GPS : repère « non renseigné », cohérence horaire absente.](../assets/captures/apercu-diagnostic-sans-gps.png)

## Quand le diagnostic ne peut pas être chargé

Un passage dont la carte SD n'a pas encore été importée n'a pas de session d'enregistrement : il n'y a
rien à diagnostiquer, et l'écran le dit dans un bandeau plutôt que de rester vide sans explication.

La **barre de statut**, en bas, continue d'indiquer de quel passage il s'agit - c'est au moment où
quelque chose ne va pas qu'on a le plus besoin de savoir sur quelle nuit on se trouve.

![L'écran de diagnostic après un échec de chargement : le bandeau explique, et la barre de statut nomme toujours le passage.](../assets/captures/apercu-diagnostic-erreur-statut.png)

## Exporter la courbe

![L'image exportée du graphe climatique : la courbe redessinée, avec sous elle le passage, le nombre de mesures et la provenance.](../assets/captures/apercu-diagnostic-export.png)
