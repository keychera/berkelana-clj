(ns engine.start-dev
  (:require [engine.start :as start]
            [clojure.spec.test.alpha :as st]
            [play-cljc.gl.core :as pc])
  (:import (gui Hello)))

(defn start []
  (st/instrument)
  (println "hello from clojure")
  (Hello/hello "msg from clojure")
  (let [window (start/->window)
        game (pc/->game (:handle window))]
    (start/start game window)))

(defn -main []
  (start)
  (shutdown-agents))
