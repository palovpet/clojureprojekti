(ns projekti.core
;  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [accountant.core :as accountant]
            [clerk.core :as clerk]
            [cljs-http.client :as http]
            [clojure.core.async :refer [<! go]]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rdom]
            [reagent.session :as session]
            [reitit.frontend :as reitit]))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]
    ["/uusi-sivu" :uusi-sivu]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Page components

(defn home-page []
  (fn []
    [:span.main
     [:h1 "Welcome to projekti"]
     [:ul
      [:li [:a {:href (path-for :items)} "Items of projekti"]]
      [:li [:a {:href "/broken/link"} "Broken link"]]]]))



(defn items-page []
  (fn []
    [:span.main
     [:h1 "The items of projekti"]
     [:ul (map (fn [item-id]
                 [:li {:name (str "item-" item-id) :key (str "item-" item-id)}
                  [:a {:href (path-for :item {:item-id item-id})} "Item: " item-id]])
               (range 1 60))]]))


(defn item-page []
  (fn []
    (let [routing-data (session/get :route)
          item (get-in routing-data [:route-params :item-id])]
      [:span.main
       [:h1 (str "Item " item " of projekti")]
       [:p [:a {:href (path-for :items)} "Back to the list of items"]]])))


(defn about-page []
  (fn [] [:span.main
          [:h1 "About projekti"]]))

(def text (reagent/atom ""))

(def choice (reagent/atom ""))

(def result (reagent/atom ""))

(defn send-request [teksti valinta]
  (println "Teksti on" teksti " valinta on " valinta)
  (go (let [response (<! (http/post "/formi" {:form-params {:teksti teksti :valinta valinta}
                                              :type :json
                                              :async? true}))]
        (reset! result (:body response)))))

(defn uusi-sivu []
  (fn [] [:span-main
          [:h1 "Uusi sivu"]
          [:form {:on-submit #(.preventDefault %)}
           [:p
            [:input {:type :text
                     :value @text
                     :on-change #(reset! text (.-value (.-target %)))}]]
           [:div {:on-change #(reset! choice (.-value (.-target %)))} 
            [:div [:label [:input {:type :radio :name "case" :value :lower}] "Pienell??"]]
            [:div [:label [:input {:type :radio :name "case" :value :upper}] "Isolla"]]]
           [:button {:type "submit"
                     :on-click #(send-request @text @choice)} "Push me ;-)"]]
          [:h2 "Teksti on:" @result]]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page
    :items #'items-page
    :item #'item-page
    :uusi-sivu #'uusi-sivu))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :uusi-sivu)} "Uusi sivu"] " | "
         [:a {:href (path-for :about)} "About projekti"]]]
       [page]
       [:footer
        [:p "projekti was generated by the "
         [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))

(defn ^:dev/after-load reload! []
  (mount-root))
