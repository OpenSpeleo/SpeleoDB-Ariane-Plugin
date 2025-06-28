# Ariane-SpeleoDB-Releases

> [!CAUTION]  
> This release is experimental - there will probably be issues -
> please [open an issue](https://github.com/OpenSpeleo/SpeleoDB-Ariane-Plugin/issues/new) or send me a message if you find any abnormal behavior or have any question.

## Installation Instructions:

### 1. Update Ariane

Download and install the latest release of Ariane: https://github.com/Ariane-s-Line/Ariane-Release/releases

> [!NOTE]  
> You need at a **bare minimum** Ariane version `25.2.1` or above.

### 2. Download the SpeleoDB Plugin

Download the latest version of the SpeleoDB Ariane Plugin: [OpenSpeleo/SpeleoDB-Ariane-Plugin/releases](https://github.com/OpenSpeleo/SpeleoDB-Ariane-Plugin/releases).

> [!NOTE]  
> You want the file in `.jar` - ignore the `.zip` and `.tar.gz`.

![Screenshot 2025-06-21 at 12 01 03](assets/tutorial1.png)

### 3. Install the SpeleoDB plugin in Ariane

1. Launch Ariane

2. Add a new plugin to Ariane

Click the `I` => plugins => add plugin

![Screenshot 2025-06-21 at 12 05 19](assets/tutorial2.png)

1. Select the `.jar` file we just downloaded
   
![Screenshot 2025-06-21 at 12 06 59](assets/tutorial3.png)

1. Quit & Restart Ariane

## Usage Instructions:

Congrats 🎉 🎉 🎉  ! You should now see a `SpeleoDB` tab on the left.

![Screenshot 2025-06-21 at 12 07 37](assets/tutorial4.png)

1. Open the `SpeleoDB` tab - should look like this (internet connection required from that point)
   
![Screenshot 2025-06-21 at 12 08 26](assets/tutorial5.png)

2. Let's connect

Let's click on the `Connection` pane - it should look like this

![Screenshot 2025-06-21 at 12 09 49](assets/tutorial6.png)

At this point you have two choices:

  1. Email & Password
  2. Enter your authentication token

If you decide to use the token, open this link: [https://www.speleodb.org/private/auth-token/](https://www.speleodb.org/private/auth-token/)

> [!CAUTION]  
> Treat this `token` as your password ! It will give complete access to your account if you share this "token".<br>
> **In case you need to change it => click on `Refresh Token` (the red button).**

![Screenshot 2025-06-21 at 12 13 13](assets/tutorial7.png)

Let's copy/paste the token in the app and click `CONNECT`

# 3. Opening a project

Once connected you should see appearing the list of project you have access to

![Screenshot 2025-06-21 at 12 15 31](assets/tutorial8.png)

Click any of the projects. This will download and load the project you want.

![Screenshot 2025-06-21 at 12 33 07](assets/tutorial9.png)

In the background, Ariane acquires the project lock for you (if you have write access and nobody is currently modifying the file).

From that point you can:

- Modify the project and upload a new revision by clicking `Save Project`

![Screenshot 2025-06-21 at 12 34 41](assets/tutorial10.png)

- Change project by going back to the `Projects` tab

- Unlock the project and quit Ariane

Hope you have fun ! Feel free to give me any feedback
