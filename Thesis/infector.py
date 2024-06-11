import requests
import json
from collections import defaultdict
import requests
import json
import random
import time
from collections import defaultdict
import threading

def connectionToManaging():
    return requests.get('http://127.0.0.1/getOnlineNodesAndTypes')
# Effettua la richiesta HTTP iniziale
response = connectionToManaging()
if response.status_code == 200:
    # Decodifica la risposta JSON
    data = response.json()  # La risposta è un hashmap: {"192.168.1.2": 1, "192.168.1.3": 0, ...}
    # Crea un dizionario per suddividere gli URL per tipo
    urls_by_type = defaultdict(list)
    for url, type in data.items():
        urls_by_type[type].append(url)
    # Inizializza le probabilità per ogni tipo a 0 se non sono già presenti, altrimenti se non inizializzati  probabilities = {type: 0 for type in urls_by_type.keys()}
    probabilities = {type: 0 for type in urls_by_type.keys()}
    # Funzione per aggiornare la probabilità di un tipo
    def update_probability(type, new_probability):
        if type in probabilities:
            probabilities[type] = new_probability
            print(f"Type {type} uptdated to {new_probability}")
        else:
            print(f"Type {type} doesn't exist.")
        #print all the available types and their probabilities
        print("Current probabilities:")
        for type, prob in probabilities.items():
            print(f"Type {type}: {prob}")
    # Funzione per eseguire il ciclo ogni secondo
    def run_cycle():
        global urls_by_type
        seconds=0
        while True:
            seconds=seconds+1
            if(seconds==60):
                response = connectionToManaging()
                if response.status_code == 200:
                    data = response.json()
                    urls_by_type = defaultdict(list)
                    for url, type in data.items():
                        urls_by_type[type].append(url)
                    seconds=0
            for type, urls in urls_by_type.items():
                for url in urls:
                    if random.random() < probabilities[type]:
                        try:
                            infect_url = f"http://{url}/infect"
                            print(f"{infect_url} infected, type {type}" )
                            requests.get(infect_url) 
                        except Exception as e:
                            print(f"Errore nella chiamata a {url}: {e}")
                            response = connectionToManaging()
                            if response.status_code == 200:
                                data = response.json()
                                urls_by_type = defaultdict(list)
                                for url, type in data.items():
                                    urls_by_type[type].append(url)
                            else:
                                print("Managing closed")
                                exit(1)
            time.sleep(1)
    # Funzione per ascoltare gli input dell'utente
    def listen_for_input():
        #stampa le probabilità attuali con i tipi corrispondenti
        for type, prob in probabilities.items():
            print(f"Type {type}: {prob}")
        while True:
            try:
                user_input = input("Insert no. type and probability (example: '1 0.5'): ")
                tipo, new_probability = user_input.split()
                tipo = int(tipo)
                new_probability = float(new_probability)
                if(new_probability < 0 or new_probability > 1):
                    print("The probability must be between 0 and 1.")
                else:
                    update_probability(tipo, new_probability)
            except ValueError:
                print("Input not valid. Please insert a no. type and a probability separated by a space.")
    # Esegui il ciclo in un thread separato
    cycle_thread = threading.Thread(target=run_cycle)
    cycle_thread.daemon = True  # Assicura che il thread si chiuda quando il programma principale termina
    cycle_thread.start()
    # Esegui la funzione di ascolto degli input in un thread separato
    input_thread = threading.Thread(target=listen_for_input)
    input_thread.daemon = True
    input_thread.start()
    # Mantieni il programma in esecuzione (o puoi usare un'altra logica per mantenere vivo il programma)
    while True:
        time.sleep(10)
else:
    print(f"Errore nella richiesta: {response.status_code}")