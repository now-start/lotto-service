import os

def read_file(file_path):
  with open(file_path, 'r') as file:
    return file.read()


def write_file(file_path, content):
  with open(file_path, 'w') as file:
    file.write(content)


def format_as_markdown(yaml_content):
  return '```yaml\n' + yaml_content + '\n```'


def replace_docker_section(readme_content, markdown):
  start = readme_content.index('```yaml')
  end = readme_content.index('```', start + 1) + 3
  return readme_content[:start] + markdown + readme_content[end:]


if __name__ == "__main__":
  script_dir = os.path.dirname(os.path.realpath(__file__))
  yaml_file_path = os.path.join(script_dir, '../../docker-compose.yaml')
  readme_file_path = os.path.join(script_dir, '../../README.md')

  yaml_content = read_file(yaml_file_path)
  markdown = format_as_markdown(yaml_content)

  readme_content = read_file(readme_file_path)
  updated_readme = replace_docker_section(readme_content, markdown)

  write_file(readme_file_path, updated_readme)
