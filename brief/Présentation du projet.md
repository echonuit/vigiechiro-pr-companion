# Présentation du projet

## Aperçu des exigences

[VigieChiro](https://www.vigienature.fr/fr/chauves-souris) est un programme de sciences participatives porté par le **Muséum national d'Histoire naturelle** (MNHN) dans le cadre de Vigie-Nature. Il s'appuie sur un réseau de bénévoles - naturalistes amateurs ou professionnels, gestionnaires d'espaces naturels, associations - qui installent ponctuellement des **enregistreurs ultrasons** sur le terrain pour suivre l'évolution des populations de chauves-souris en France métropolitaine.

Une partie de ce réseau utilise un **Passive Recorder (PR)** open-hardware basé sur la plateforme Teensy, dont le firmware est développé en open-source ([PiBatRecorderProjects/TeensyRecorders](https://framagit.org/PiBatRecorderProjects/TeensyRecorders)). Le PR est laissé seul sur un point d'écoute pendant une nuit entière. Il s'allume au crépuscule, enregistre tout signal ultrason détecté en bande 8-120 kHz à 384 kHz d'échantillonnage, et se rendort à l'aube. Une nuit type produit **plusieurs centaines à plusieurs milliers de fichiers WAV**, accompagnés d'un journal technique (`LogPR<sn>.txt`) et d'un journal de température / hygrométrie (`PaRec<sn>_THLog.csv`).

Une fois la nuit terminée, le possesseur du PR récupère la carte SD, dépose les enregistrements sur la plateforme VigieChiro, qui les fait passer dans le pipeline d'analyse automatique **Tadarida** (logiciel scientifique de classification développé par le MNHN et le CNRS). Tadarida découpe les WAV en évènements sonores, les classifie en taxons (espèces de chauves-souris, mais aussi bruit ambiant et oiseaux), et restitue le résultat sous forme d'un **CSV d'observations**. Le possesseur du PR doit ensuite **valider ou corriger** les espèces proposées par Tadarida avant que les données ne soient consolidées dans la base nationale et qu'un validateur expert ne tranche en dernier ressort.

Aujourd'hui, ce travail de suivi des campagnes et de pré-validation des observations se fait avec un mélange d'outils dispersés : explorateur de fichiers, tableur, lecteur audio, plateforme VigieChiro en ligne. Les utilisateurs réclament un **outil unique** qui réunit tout cela au même endroit pour rendre la démarche plus fluide et plus accessible.

## Objectifs principaux

L'application à développer doit permettre au **possesseur d'un Passive Recorder** de :

- **Centraliser** ses campagnes de capture dans un journal personnel : date, lieu, paramètres d'acquisition, statut (brut, envoyé à VigieChiro, résultats reçus, validé) ;
- **Importer** une nuit de capture depuis un dossier (WAV bruts + log technique + log T°/hygro) sans étape manuelle ;
- **Visualiser** les conditions de capture (graphes de température/hygrométrie, état des batteries, événements du journal technique) ;
- **Charger** les CSV de résultats Tadarida récupérés depuis VigieChiro et les croiser avec les WAV correspondants ;
- **Parcourir** les observations classifiées : tri, filtrage par taxon ou par probabilité, écoute de l'évènement sonore associé (lecture ralentie pour rendre l'ultrason audible) ;
- **Valider ou corriger** la classification proposée par Tadarida avant le retour à VigieChiro ;
- **Exporter** un fichier de validation au format attendu par VigieChiro (le CSV `…observations_Vu.csv` que la plateforme sait ré-ingérer).

L'application doit également :

- **respecter les normes d'accessibilité** afin que des utilisateurs souffrant de déficiences visuelles légères puissent l'utiliser confortablement (contraste, taille de police, raccourcis clavier) ;
- **fonctionner hors-ligne** : une nuit de terrain peut produire des giga-octets de données et l'utilisateur doit pouvoir travailler sans connexion ;
- **être portable** sur Windows, Linux et macOS sans installation système lourde.

## Parties prenantes

| Acteur | Rôle |
|---|---|
| **Possesseur de PR** (utilisateur principal) | Naturaliste amateur ou professionnel qui exploite un PR. Il installe l'appareil sur le terrain, récupère la carte SD, importe les données dans l'application, valide les classifications, exporte vers VigieChiro. La plupart ne sont pas informaticiens : l'application doit être abordable. |
| **Plateforme VigieChiro** (système amont/aval) | Reçoit les fichiers du possesseur et restitue les CSV Tadarida. L'application n'a pas à dialoguer en direct avec la plateforme - les échanges se font par téléversement / téléchargement de fichiers. |
| **Validateur expert** (rôle aval) | Ornithologue/chiroptérologue de l'équipe VigieChiro qui tranche les cas litigieux après le passage du possesseur. L'application n'a pas à gérer ce rôle, mais elle doit produire un export propre qui lui facilite la tâche. |
| **Équipe pédagogique R2.02 / R2.03** (commanditaire) | Joue le rôle du client. Évalue les livrables aux trois jalons. Donne les critères d'arbitrage en cas de doute. |

> 💡 Dans la première version, l'application reste mono-utilisateur : pas de comptes, pas de synchronisation cloud, pas de gestion d'équipe. Le possesseur de PR est seul devant son ordinateur, l'application travaille sur ses fichiers locaux.
