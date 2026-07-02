# WebComputationalGraph

**WebComputationalGraph** is a Java framework for building and running computational graphs on a publisher/subscriber architecture, complete with a custom HTTP server and a browser-based dashboard for configuring, visualizing, and driving graphs in real time.

> Built as part of the Advanced Programming course (BIU).

**Author:** [Ziv Chaba](https://github.com/Ziv33)

---

<p align="center">
  <img width="1920" height="908" alt="image" src="https://github.com/user-attachments/assets/4db0d17d-6c26-4c91-958f-3633a6f408af" />
  <br>
  <em>System Computation Topology - incAddMulText.conf Example </em>
</p>

---

## Table of Contents

- [Background](#background)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Installation](#installation)
- [Running the Project](#running-the-project)
- [Using the Dashboard](#using-the-dashboard)
- [Configuration File Format](#configuration-file-format)
- [Design Patterns Used](#design-patterns-used)
- [Documentation (Javadoc)](#documentation-javadoc)
- [Demo Video and Presentation](#demo-video-and-presentation)

---

## Background

This project delivers an end-to-end platform for defining, running, and monitoring computational graphs.

At its core is a publisher/subscriber engine: independent agents perform computations and communicate exclusively through named topics, allowing complex pipelines to be composed simply by wiring agents to shared channels. Every agent runs concurrently and safely, thanks to a thread-safe topic registry and an Active Object execution model that keeps slow computations from blocking the rest of the system.

Around this engine sits a fully custom web application layer, built directly on Java sockets rather than any existing web framework — including its own HTTP request parser, routing layer, and thread-pooled server. This powers a live browser dashboard that lets users upload a graph configuration, watch it rendered as an interactive diagram, and publish messages to any topic to see the graph update in real time.

The result is a complete demonstration of concurrent system design, clean layered architecture, and several classic software design patterns (Observer, Decorator, Active Object, Singleton, Strategy, and Flyweight) applied to a real, working product.

## Architecture

The system follows a classic MVC layering:

| Layer | Package | Responsibility |
|---|---|---|
| **Model** | `graph`, `configs` | Messages, topics, agents, the topic registry, and the graph/config abstractions that wire agents together |
| **Controller** | `server`, `servlets` | A generic multithreaded HTTP server and the servlets that route requests to model-layer operations |
| **View** | `views`, `html_files` | Server-side HTML generation (`HtmlGraphWriter`) plus the static and dynamic pages rendered in the browser |
| **Composition Root** | `run` | `Main` — wires servlets to routes and starts the server |

The browser sends a request, the server figures out which servlet should handle it, that servlet performs the relevant operation on the graph, and the result is sent back to the browser — either as an updated table of values or an updated graph visualization.

## Project Structure

```
WebComputationalGraph/
├── src/
│   ├── graph/          # Message, Topic, Agent, TopicManagerSingleton, Node/Graph (cycle detection),
│   │                   # ParallelAgent (Active Object decorator), and sample agents
│   │                   # (PlusAgent, IncAgent, BinOpAgent, MulAgent, TextMergeAgent)
│   ├── configs/        # Config interface, GenericConfig (reflection-based config loader),
│   │                   # MathExampleConfig (sample hard-coded graph)
│   ├── server/         # HTTPServer interface, MyHTTPServer implementation, RequestParser
│   ├── servlets/        # Servlet interface, TopicDisplayer, ConfLoader, HtmlLoader
│   ├── views/           # HtmlGraphWriter — renders a Graph instance into HTML/JS
│   └── run/              # Main — application entry point
├── html_files/          # Static/templated front-end assets served under /app/
│   ├── index.html        # 3-frame dashboard shell
│   ├── form.html          # config upload + publish-message forms
│   ├── graph.html          # vis-network based graph visualizer
│   ├── active_graph.html    # generated per-request graph view
│   ├── temp.html             # blank placeholder frame
│   └── js/vis-network(.min).js
├── config_files/        # Sample configuration files (see below)
├── doc/                 # Generated Javadoc (open doc/index.html)
├── WebComputationalGraphPresentation.pptx  # Slides used in the project demo video
└── .gitignore           # Excludes compiled output (bin/) and IDE project files from version control
```

## Installation

**Requirements:** Java 8+ (JDK), no external dependencies or build tool required.

1. Clone the repository:
   ```bash
   git clone https://github.com/Ziv33/WebComputationalGraph.git
   cd WebComputationalGraph
   ```
2. Compile the sources:
   ```bash
   mkdir -p bin
   javac -d bin -cp src $(find src -name "*.java")
   ```
   Alternatively, import the `src` folder into Eclipse (or any Java IDE) as a new Java project.

## Running the Project

Start the server from the compiled classes:

```bash
java -cp bin run.Main
```

This will:
1. Start `MyHTTPServer` on **port 8080** with a pool of **5** worker threads.
2. Register the routes:
   - `GET /publish` → `TopicDisplayer`
   - `POST /upload` → `ConfLoader`
   - `GET /app/` → `HtmlLoader` (serving files from `html_files/`)
3. Block on `System.in`, so the server keeps running until you press **Enter** in the terminal, at which point it shuts down cleanly (closes sockets and the thread pool).
Once running, open a browser at: [http://localhost:8080/app/index.html](http://localhost:8080/app/index.html)

## Using the Dashboard

The dashboard (`index.html`) is split into three panels:

- **Left — Controls:**
  - *Load Configuration* — choose a `.conf` file and click **Deploy** to upload it via `POST /upload`. The server parses it, builds the agent graph, checks it for cycles, and returns a live graph visualization rendered in the center panel.
  - *Send Message* — pick a topic name and value and click **Send** to publish via `GET /publish`, updating the running graph.
- **Center — Graph View:** an interactive visualization of the computational graph — topics as rectangles, agents as circles, edges following the data flow direction.
- **Right — Values Table:** the current value held by every active topic, refreshed after each publish.

## Configuration File Format

A configuration file defines a set of agents, three lines per agent:

```
<fully-qualified class name of the agent>
<comma-separated list of topics this agent subscribes to>
<comma-separated list of topics this agent may publish to>
```

Example (`config_files/simple.conf`):

```
graph.PlusAgent
A,B
C
graph.IncAgent
C
D
```

This wires `PlusAgent` to consume `A` and `B` and publish their sum to `C`, and `IncAgent` to consume `C`, increment it, and publish the result to `D`.

Agents are instantiated reflectively via `getConstructor(String[].class, String[].class)`, so any `Agent` implementation exposing a `(String[] subs, String[] pubs)` constructor can be dropped into a config file without any code changes to the server. Several sample configs are provided under `config_files/` (including one that intentionally contains a cycle, used to exercise cycle detection).

## Design Patterns Used

- **Observer** — `Topic` notifies all subscribed `Agent`s via `callback()` when a message is published.
- **Decorator** — `ParallelAgent` wraps any `Agent` to add concurrent execution.
- **Active Object** — `ParallelAgent` decouples the publishing thread from the agent's execution thread using an internal `BlockingQueue` and a dedicated worker thread.
- **Singleton (lazy, thread-safe via the initialization-on-demand holder idiom)** — `TopicManagerSingleton` guarantees a single, thread-safe registry of topics without explicit locking.
- **Strategy** — `BinOpAgent` accepts a `BinaryOperator<Double>` lambda, letting the binary operation be supplied as a parameter.
- **Flyweight** — `TopicManager.getTopic()` returns a shared `Topic` instance per name, creating one only on first request.
- **Front Controller / MVC** — `MyHTTPServer` + `Servlet` implementations route all HTTP requests through a single dispatch point to decoupled controller components.

## Documentation (Javadoc)

Full API documentation has been generated and is available **[here](https://html-preview.github.io/?url=https://github.com/Ziv33/WebComputationalGraph/blob/main/doc/index.html)**. Click the link to browse the complete API reference, including usage examples.

## Demo Video and Presentation

A walkthrough of the project — background, design, and a live demo of configuration upload, graph visualization, and message publishing — is available **[here]**

The slides used in the presentation can be found in: [WebComputationalGraphPresentation.pptx](WebComputationalGraphPresentation.pptx).
