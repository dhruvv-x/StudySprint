# StudySprint

A small Android app I made to plan and run study sessions.

You add your subjects, add tasks under them, pick a few tasks and set how many minutes each one gets. The app lines them up one after another, with short breaks in between, and runs the timer for you. When the session is over you can see how it went.

It works fully offline. Everything is stored on the phone, nothing is sent anywhere.

## What it does

- Subjects and tasks, with a priority (low, medium, high) for each task
- Create a session by picking tasks and setting minutes per task
- Optional breaks between tasks (never before the first or after the last one)
- Active session screen with pause, resume and skip
- History of past sessions
- Analytics to see how much you studied
- Settings, including break length

## Rules, not AI

The scheduling is plain rule based code. There is no AI or network call anywhere in the app. The rules are small enough to read in a couple of minutes:

- If a task runs over its time, the next break gets shorter by half of the extra time. A break never drops below half its planned length, or 1 minute.
- If you skip a task within the first minute, the break after it is removed.
- If you finish a task a minute or more early, the app tells you the session will end sooner.
- Time suggestions come from your history. If a task has been completed at least twice, the app suggests the average time you actually took.

You can find these in `domain/scheduler/`.

The timer does not count seconds in a loop. It saves timestamps in the database and works out the time from those, so the time stays correct even if Android kills the app in the background.

## Built with

- Kotlin
- Jetpack Compose and Material 3
- Room for the database
- DataStore for settings
- Navigation Compose
- Gradle with Kotlin DSL and a version catalog

Min SDK 24, target SDK 36.

## Project layout

```
app/src/main/java/com/pookie/studysprint/
  data/        Room database, DAOs, repositories, settings
  domain/      models and the scheduling rules
  engine/      the session engine and timer math
  history/     history screen
  ui/          screens, view models, theme and navigation
```

## Running it

1. Clone the repo
2. Open it in Android Studio
3. Let Gradle sync
4. Run the `app` configuration on an emulator or a phone

## Tests

Unit tests for the timer math and the scheduling rules are in `app/src/test`. Tests for the session engine and recovery are in `app/src/androidTest` and need a device or emulator.

To run the unit tests from the terminal:

```
./gradlew test
```

## Status

Version 1.0, still a work in progress.
