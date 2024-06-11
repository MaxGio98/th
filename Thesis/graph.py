
import requests
import networkx as nx
import matplotlib.pyplot as plt

responses = []
start_online_nodes = []

# Funzione per ottenere i vicini da un nodo
def get_neighbors(node):
    url = f"http://{node}/showTable"
    try:
        
        response = requests.get(url)
        return response.json()
    except requests.exceptions.RequestException as e:
        print("showTable - Server closed or unreachable. Please restart the server and try again.")
        exit(1)

# Funzione per ottenere lo stato di infezione di un nodo
def get_infection_status(node):
    url = f"http://{node}/isInfected"
    try:
        response = requests.get(url)
        return response.json()
    except requests.exceptions.RequestException as e:
        print("isInfected - Server closed or unreachable. Please restart the server and try again.")
        exit(1)

# Funzione per creare il grafico dei nodi e archi
def create_graph():
    url = 'http://127.0.0.1/getOnlineNodes'
    response = requests.get(url)        #response contiene un array di stringhe con gli indirizzi ip dei nodi online
    online_nodes = response.json()

    global start_online_nodes
    start_online_nodes = online_nodes
    G = nx.Graph()
    for node in online_nodes:
        neighbors = get_neighbors(node)
        responses.append(neighbors)
        for neighbor, info in neighbors.items():
            if info["distance"] == 1:
                G.add_edge(node, str(neighbor))
        # Aggiungi stato di infezione come attributo al nodo
        infection_status = get_infection_status(node)
        G.nodes[node]["infected"] = infection_status[0]  # booleano
        G.nodes[node]["infection_value"] = infection_status[1]  # intero
    return G

# Funzione per disegnare il grafico

def draw_graph(graph, pos):
    while True:
        global start_online_nodes
        

        # Aggiorna gli stati di infezione per ogni nodo
        for node in graph.nodes():
            infection_status = get_infection_status(node)
            graph.nodes[node]["infected"] = infection_status[0]  # booleano
            graph.nodes[node]["infection_value"] = infection_status[1]  # intero

        # Disegna i nodi
        node_colors = ["red" if graph.nodes[node]["infected"] else "green" for node in graph.nodes()]
        plt.clf()  # Pulisce la figura prima di disegnare il nuovo grafico
        nx.draw(graph, pos, with_labels=False, node_size=1000, node_color=node_colors, font_size=8, font_weight="bold")
        nx.draw_networkx_edges(graph, pos, width=1.0, alpha=0.5)
        
        # Aggiungi le etichette dei nodi
        for node in graph.nodes():
            nx.draw_networkx_labels(graph, pos, {node: node}, font_size=8, font_color="black", font_weight="bold", verticalalignment="bottom", horizontalalignment="center")
            nx.draw_networkx_labels(graph, pos, {node: str(graph.nodes[node]['infection_value'])}, font_size=8, font_color="black", font_weight="normal", verticalalignment="top", horizontalalignment="center")

        plt.title("Topology")
        plt.pause(5)  # Aggiorna l'immagine ogni 5 secondi

        if not plt.fignum_exists(1):
            break
        
        url = f"http://127.0.0.1/getOnlineNodes"
        #controlla che non ci sia errore nella richiesta
        try:
            response = requests.get(url)
            online_nodes = response.json()
        except requests.exceptions.RequestException as e:
            print("getOnlineNodes - Server closed or unreachable. Please restart the server and try again.")
            print(e)
            exit(1)
        #se la lista dei nodi online è cambiata, aggiorna il grafo
        if online_nodes != start_online_nodes:
            #crea un nuovo grafo, mantenendo i nodi e gli archi del grafo precedente
            graph = create_graph()
            pos = nx.spring_layout(graph)
            start_online_nodes = online_nodes
        else:        
            for node in online_nodes:
                neighbors = get_neighbors(node)
                for neighbor, info in neighbors.items():
                    if info["distance"] == 1:
                        if not graph.has_edge(node, str(neighbor)):
                            graph.add_edge(node, str(neighbor))
                    else:
                        if graph.has_edge(node, str(neighbor)):
                            graph.remove_edge(node, str(neighbor))

# Funzione principale
def main():
    graph = create_graph()
    pos = nx.spring_layout(graph)  # Calcola la disposizione dei nodi una volta sola
    draw_graph(graph, pos)

if __name__ == "__main__":
    main()