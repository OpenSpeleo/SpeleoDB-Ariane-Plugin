## Feature List - SpeleoDB

- [x] Make the "signup" button to open a browser page to: https://{instance}/signup/

- [x] Allow the user to either specify (Email && Password) || Token
  - I should have done most of it. If user gives token, verify it works by calling **GET:** `/api/v1/user/auth-token/` (should be done already).

- [x] Verifying the webpage `https://www.speleodb.org/webview/ariane/` loads well in the `aboutWebView`. Please send me a screenshot.

- [x] Project Creation:
  - [x] Add a button in the "projects" tab to create a new project.
  - [x] Add a form that replicates: https://www.speleodb.org/private/projects/new/ using the API: **POST:** `/api/v1/projects/`
  - [x] Immediately "acquire the lock" on the project (once the project has been created)
  - [x] Immediately after the lock => trigger a "refresh" of the project listing so that it appears.

- [ ] Save UX:
  - [ ] Allow the User to save with CTRL + S / CMD + S
  - [ ] Show a pop-up / modal asking for a commit message and boom (the modal should include a cancel button).

- [ ] "Open Project" UX
  - [ ] On open, ask the user if they want "read-only or "edition".

    - [ ] If "Edition" => Acquire the lock **then** download the project (not the reverse, classic mutex stuff).

    - [ ] If "Read-only" => Don't acquire the lock and prevent modifications

      - [ ] If detecting changes tried to be made => asking the user if they want to open the project in "edition mode", if yes probably safer to acquire the lock & re-download the project (in case someone else modified the project in your back).

  - [ ] If the project has already a lock, mentions that the project can only be opened in "read-only" and who is currently "editing the file" (Name and/or email)

- [x] "List Projects" UX
  - [x] Add a refresh button

- [ ] Error management. We need clear error messages and invite people to contact me (or you) with a "useful log" to debug any issue.

- [ ] Adding a periodic 5min (sounds reasonable to me) "re-acquire the mutex" background task while editing a file (if online). This allows to create a "heartbeat" of when was the user "last seen editing the file".
  - [ ] Make it "okay to fail" if the user is disconnected from network / network busy. Only notify the user **on first failure** if the server is sending an error.

## Feature List - In general

- [ ] Add a SpeleoDB_ID field to the TML/TMLU - we might do some stuff with it later (like recovery of project, where to upload it).
  - [ ] We will at some point add some "project fork" logic. I don't yet have a good idea on how to manage this.

- [ ] Save every DMP "section" inside the TML as an independant DMP file (check hashes to prevent duplicates).
  - [ ] I recommend a mix of "timestamp and filehash" for the name something like `mnemo_{section_time.strftime("%Y-%m-%d_%Hh%M")_{sha1hash[:8]}}` (only the first 8 chars of the hash to prevent collisions).
  - [ ] Potentially you can use the file hash (extracted with a regex) to "match" the file and avoid duplicates instead of recomputing them. Though it's extremely cheap for such small files.

## Feature List - I'm not sure if/how we should do that

- [ ] We need a way to allow users to "lock the project" for a bunch of days / weekends / weeks. Mostly if the user is working offline.

- [ ] What if we start working "online" download the project and then get offline (plane ? remote ?)

- [ ] Shall we do a sort of "offline queue" for the commits that gets cleared next time Ariane gets online ?

- [ ] Shall we ask the user for save & release the mutex when Ariane is closing ? I really want to avoid a situation where people acquire locks all over the place and never release them.

- [ ] If `CheckEqualOrUpdateSpeleoDB_ID` (previously `checkFileId`) doesn't "match", maybe we should issue a warning to the user and asking for confirmation. That would prevent mistakes. The main reason I see this happening would be "a project fork" essentially (or a mistake).

- [ ] Provide a list of the different revisions of the projects, and allow the user to download in READ_ONLY any version of the project.
